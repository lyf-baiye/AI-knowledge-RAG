package com.knowledgemanager.vector.service;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgemanager.common.entity.Chunk;
import com.knowledgemanager.common.entity.ProcessingRule;
import com.knowledgemanager.common.mapper.ChunkMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.bm25.BM25Encoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 原生混合向量服务 (Hybrid Vector Service)
 * 
 * 职责：
 * 1. 文件处理全流程：提取文本 -> 智能分片 -> 存入 MySQL -> 存入 Pinecone (REST API)
 * 2. 混合检索：将 Query 转为 Dense + Sparse 向量，通过 REST API 检索
 * 3. 数据删除：删除 MySQL 记录及 Pinecone 中的向量
 */
@Slf4j
@Service
public class HybridVectorService {

    @Resource
    private TextEmbedding dashScopeClient;

    @Resource
    private TextExtractionService textExtractionService;

    @Resource
    private TextChunkService textChunkService;

    @Resource
    private ChunkMapper chunkMapper;

    @Value("${langchain4j.dashscope.api-key}")
    private String dashscopeApiKey;

    @Value("${langchain4j.pinecone.api-key}")
    private String pineconeApiKey;

    @Value("${langchain4j.pinecone.index-url}")
    private String indexUrl;

    private RestTemplate restTemplate;
    
    // 使用全局单一BM25编码器实例
    private BM25Encoder bm25Encoder;

    @PostConstruct
    public void init() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        this.restTemplate = new RestTemplate(factory);
        this.bm25Encoder = null;
        log.info("HybridVectorService initialized via REST API.");
    }

    /**
     * 核心功能 1：完整的文件处理流程
     * 接收文件 -> 提取文本 -> 分片 -> 向量化 -> 存储到 MySQL 和 Pinecone
     */
    @Transactional
    public List<Chunk> processAndUpsertFile(MultipartFile file, Long fileId, Long knowledgeBaseId, ProcessingRule rule) {
        try {
            // 1. 提取文本
            String text = textExtractionService.extractText(file);
            if (text == null || text.trim().isEmpty()) {
                throw new RuntimeException("文件内容为空");
            }
            log.info("Extracted {} characters", text.length());

            // 2. 语义相似度分片
            int chunkSize = rule != null && rule.getChunkSize() != null ? rule.getChunkSize() : 500;
            double similarityThreshold = 0.7;

            List<TextSegment> segments = textChunkService.chunk(text, fileId, knowledgeBaseId, chunkSize, similarityThreshold);
            log.info("Chunked into {} segments", segments.size());

            // 3. 训练或增量更新BM25Encoder
            if (this.bm25Encoder == null) {
                // 首次上传：全量训练
                List<TextSegment> allSegments = getAllSegmentsForKnowledgeBase(knowledgeBaseId, fileId);
                allSegments.addAll(segments);
                log.info("Initial BM25 training with all {} segments", allSegments.size());
                BM25Encoder newEncoder = BM25Encoder.builder()
                        .tokenizer(textStr -> tokenize(textStr))
                        .build();
                newEncoder.train(allSegments);
                this.bm25Encoder = newEncoder;
            } else {
                // 后续上传：增量更新（仅处理新分片，O(newTerms) 而非 O(allTerms × allDocs)）
                log.info("Incremental BM25 update with {} new segments", segments.size());
                this.bm25Encoder.updateTrain(segments);
            }

            List<Chunk> chunkEntities = new ArrayList<>();

            // 5. 遍历分片：生成稠密和稀疏向量并存储
            for (int i = 0; i < segments.size(); i++) {
                TextSegment segment = segments.get(i);
                String content = segment.text();

                // 5.1 保存分片到 MySQL (Chunk 表)
                Chunk chunk = new Chunk();
                chunk.setFileId(fileId);
                chunk.setContent(content);
                chunk.setChunkIndex(i);
                chunk.setMetadata("{}"); // 简化处理
                chunk.setDeleted(0);
                chunkMapper.insert(chunk);
                
                // 更新 ID，用于关联
                String docId = "chunk_" + chunk.getId();

                // 5.2 使用text-embedding-v3生成稠密向量
                TextEmbeddingParam param = TextEmbeddingParam.builder()
                        .apiKey(dashscopeApiKey)
                        .model("text-embedding-v3")
                        .text(content)
                        .build();
                
                TextEmbeddingResult result = dashScopeClient.call(param);
                List<TextEmbeddingResultItem> items = result.getOutput().getEmbeddings();
                if (items == null || items.isEmpty()) {
                    throw new RuntimeException("阿里云模型未返回向量数据");
                }

                TextEmbeddingResultItem item = items.get(0);
                List<Float> denseVectorFloat = item.getEmbedding().stream()
                        .map(Double::floatValue)
                        .collect(Collectors.toList());

                // 5.3 使用已训练的BM25Encoder生成稀疏向量
                List<Integer> sparseIndices = new ArrayList<>();
                List<Double> sparseValues = new ArrayList<>();
                
                // 使用已训练的BM25Encoder生成稀疏向量
                dev.langchain4j.store.embedding.bm25.BM25Encoder.CustomEmbedding customEmbedding = 
                    (dev.langchain4j.store.embedding.bm25.BM25Encoder.CustomEmbedding) this.bm25Encoder.embed(segment);
                float[] sparseVectorArray = customEmbedding.vectorAsArray();
                
                // 转换为Pinecone格式（只保留非零值）
                for (int idx = 0; idx < sparseVectorArray.length; idx++) {
                    if (Math.abs(sparseVectorArray[idx]) > 1e-10) { // 非零值
                        sparseIndices.add(idx);
                        sparseValues.add((double) sparseVectorArray[idx]);
                    }
                }

                // 5.4 构建 REST API 请求体 (Upsert)
                JSONObject vectorJson = new JSONObject();
                vectorJson.put("id", docId);
                vectorJson.put("values", denseVectorFloat); // 使用Float类型
                
                JSONObject sparseVectorJson = new JSONObject();
                sparseVectorJson.put("indices", sparseIndices);
                sparseVectorJson.put("values", sparseValues);
                vectorJson.put("sparse_values", sparseVectorJson);

                JSONObject metadata = new JSONObject();
                metadata.put("fileName", file.getOriginalFilename());
                metadata.put("content", content); // 存入内容以便检索后直接使用
                metadata.put("fileId", fileId); // 添加fileId到metadata便于删除
                vectorJson.put("metadata", metadata);

                JSONObject body = new JSONObject();
                body.put("vectors", new ArrayList<>()); // 注意：这里用 List
                body.getJSONArray("vectors").add(vectorJson);
                body.put("namespace", "default");

                // 5.5 发送 Upsert 请求
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Api-Key", pineconeApiKey);
                HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
                
                String url = indexUrl + "/vectors/upsert";
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                
                if (!response.getStatusCode().is2xxSuccessful()) {
                    throw new RuntimeException("Pinecone Upsert failed: " + response.getBody());
                }

                chunkEntities.add(chunk);
            }

            return chunkEntities;

        } catch (Exception e) {
            log.error("Failed to process file {}: {}", fileId, e.getMessage(), e);
            throw new RuntimeException("文件处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取知识库中所有分片
     */
    private List<TextSegment> getAllSegmentsForKnowledgeBase(Long knowledgeBaseId, Long excludeFileId) {
        // 从数据库查询该知识库中所有已有的chunks（排除当前正在处理的文件）
        LambdaQueryWrapper<Chunk> wrapper = new LambdaQueryWrapper<>();
        // 注意：这里可能需要根据实际的数据库结构调整查询条件
        // 当前假设通过fileId可以关联到知识库，但实际可能需要一个中间表来关联文件和知识库
        wrapper.ne(excludeFileId != null, Chunk::getFileId, excludeFileId); // 排除当前正在处理的文件
        List<Chunk> chunks = chunkMapper.selectList(wrapper);
        
        // 将chunks转换为TextSegment列表
        return chunks.stream()
                .map(chunk -> TextSegment.from(chunk.getContent()))
                .collect(Collectors.toList());
    }

    /**
     * 核心功能 2：删除文件相关数据
     * 删除 MySQL 中的 Chunk 记录 + 删除 Pinecone 中的向量
     */
    @Transactional
    public void deleteByFileId(Long fileId) {
        try {
            // 1. 查询该文件的所有分块ID（用于拼装Pinecone向量ID）
            List<Chunk> chunks = chunkMapper.selectList(
                new LambdaQueryWrapper<Chunk>().eq(Chunk::getFileId, fileId)
            );

            // 2. 从 Pinecone 批量删除向量
            if (!chunks.isEmpty()) {
                List<String> vectorIds = chunks.stream()
                    .map(chunk -> "chunk_" + chunk.getId())
                    .collect(Collectors.toList());

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Api-Key", pineconeApiKey);

                JSONObject body = new JSONObject();
                body.put("ids", vectorIds);
                body.put("namespace", "default");

                HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
                String url = indexUrl + "/vectors/delete";
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    log.info("Deleted {} vectors from Pinecone for fileId={}", vectorIds.size(), fileId);
                } else {
                    log.warn("Pinecone delete returned non-200 for fileId={}: {}", fileId, response.getBody());
                }
            }

            // 3. 从 MySQL 删除 chunk 记录
            chunkMapper.delete(new LambdaQueryWrapper<Chunk>().eq(Chunk::getFileId, fileId));

        } catch (Exception e) {
            log.error("Failed to delete file data for fileId={}: {}", fileId, e.getMessage(), e);
        }
    }

    /**
     * 核心功能 3：原生混合检索
     */
    public List<Map<String, Object>> hybridSearch(String query, int topK) {
        try {
            // 1. 将 Query 转化为稠密向量 (使用text-embedding-v3)
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .apiKey(dashscopeApiKey)
                    .model("text-embedding-v3")
                    .text(query)
                    .build();
            
            TextEmbeddingResult result = dashScopeClient.call(param);
            List<TextEmbeddingResultItem> items = result.getOutput().getEmbeddings();
            if (items == null || items.isEmpty()) return new ArrayList<>();
            TextEmbeddingResultItem item = items.get(0);

            List<Float> denseQueryVectorFloat = item.getEmbedding().stream()
                    .map(Double::floatValue)
                    .collect(Collectors.toList());
            
            // 2. 使用BM25Encoder生成查询的稀疏向量
            List<Integer> sparseIndices = new ArrayList<>();
            List<Double> sparseValues = new ArrayList<>();
            
            if (this.bm25Encoder != null) {
                TextSegment querySegment = TextSegment.from(query);
                dev.langchain4j.store.embedding.bm25.BM25Encoder.CustomEmbedding customEmbedding = 
                    (dev.langchain4j.store.embedding.bm25.BM25Encoder.CustomEmbedding) this.bm25Encoder.embed(querySegment);
                float[] sparseVectorArray = customEmbedding.vectorAsArray();
                
                // 转换为Pinecone格式（只保留非零值）
                for (int idx = 0; idx < sparseVectorArray.length; idx++) {
                    if (Math.abs(sparseVectorArray[idx]) > 1e-10) { // 非零值
                        sparseIndices.add(idx);
                        sparseValues.add((double) sparseVectorArray[idx]);
                    }
                }
            }

            // 3. 构建 Query JSON
            JSONObject body = new JSONObject();
            body.put("vector", denseQueryVectorFloat); // 使用Float类型
            
            JSONObject sparseVectorJson = new JSONObject();
            sparseVectorJson.put("indices", sparseIndices);
            sparseVectorJson.put("values", sparseValues);
            body.put("sparse_vector", sparseVectorJson);

            body.put("top_k", topK);
            body.put("include_metadata", true);
            body.put("namespace", "default");

            // 4. 发送请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Api-Key", pineconeApiKey);
            HttpEntity<String> entity = new HttpEntity<>(body.toJSONString(), headers);
            
            String url = indexUrl + "/query";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                // 5. 解析结果
                JSONObject jsonResponse = JSON.parseObject(response.getBody());
                List<Map<String, Object>> results = new ArrayList<>();
                
                if (jsonResponse.containsKey("matches")) {
                    for (Object matchObj : jsonResponse.getJSONArray("matches")) {
                        JSONObject match = (JSONObject) matchObj;
                        Map<String, Object> res = new HashMap<>();
                        res.put("id", match.getString("id"));
                        res.put("score", match.getDouble("score"));
                        if (match.containsKey("metadata")) {
                            res.put("metadata", match.getJSONObject("metadata"));
                        }
                        results.add(res);
                    }
                }
                return results;
            } else {
                throw new RuntimeException("Pinecone Query failed: " + response.getBody());
            }
        } catch (Exception e) {
            log.error("Hybrid search failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 中英文混合分词器
     * 中文：字符级 bigram + unigram
     * 英文：按空白和标点切分单词
     */
    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return tokens;
        }

        StringBuilder chineseBuffer = new StringBuilder();
        StringBuilder englishBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                if (englishBuffer.length() > 0) {
                    tokens.add(englishBuffer.toString().toLowerCase());
                    englishBuffer.setLength(0);
                }
                chineseBuffer.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                if (chineseBuffer.length() > 0) {
                    addChineseTokens(tokens, chineseBuffer.toString());
                    chineseBuffer.setLength(0);
                }
                englishBuffer.append(c);
            } else {
                if (chineseBuffer.length() > 0) {
                    addChineseTokens(tokens, chineseBuffer.toString());
                    chineseBuffer.setLength(0);
                }
                if (englishBuffer.length() > 0) {
                    tokens.add(englishBuffer.toString().toLowerCase());
                    englishBuffer.setLength(0);
                }
            }
        }

        if (chineseBuffer.length() > 0) {
            addChineseTokens(tokens, chineseBuffer.toString());
        }
        if (englishBuffer.length() > 0) {
            tokens.add(englishBuffer.toString().toLowerCase());
        }

        return tokens;
    }

    private void addChineseTokens(List<String> tokens, String chineseText) {
        // unigram: 每个字单独作为一个 token
        for (int i = 0; i < chineseText.length(); i++) {
            tokens.add(String.valueOf(chineseText.charAt(i)));
        }
        // bigram: 相邻两个字组成一个 token
        for (int i = 0; i < chineseText.length() - 1; i++) {
            tokens.add(chineseText.substring(i, i + 2));
        }
    }

    private boolean isChinese(char c) {
        return Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN;
    }
}