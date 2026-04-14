package com.knowledgemanager.vector.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgemanager.common.dto.RAGResultDTO;
import com.knowledgemanager.common.entity.Chunk;
import com.knowledgemanager.common.entity.ProcessingRule;
import com.knowledgemanager.common.mapper.ChunkMapper;
import com.knowledgemanager.common.mapper.FileMapper;
import com.knowledgemanager.common.util.IdGenerator;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Service
public class VectorService {

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private ChunkMapper chunkMapper;

    @Resource
    private FileMapper fileMapper;

    @Resource
    private TextExtractionService textExtractionService;

    @Resource
    private TextChunkService textChunkService;

    /**
     * 完整的文件处理流程：上传→提取→分片→向量化→存储
     */
    @Transactional
    public List<Chunk> processFile(MultipartFile file, Long fileId, Long knowledgeBaseId,
                                    String fileType, ProcessingRule rule) {
        log.info("Processing file: fileId={}, type={}, knowledgeBaseId={}", fileId, fileType, knowledgeBaseId);

        try {
            // 1. 提取文本 (使用 Tika，自动识别格式)
            String text = textExtractionService.extractText(file);
            if (text == null || text.trim().isEmpty()) {
                throw new RuntimeException("文件内容为空");
            }
            log.info("Text extracted: {} characters", text.length());

            // 2. 文本分片
            int chunkSize = rule != null && rule.getChunkSize() != null ? rule.getChunkSize() : 500;
            int overlap = rule != null && rule.getChunkOverlap() != null ? rule.getChunkOverlap() : 50;
            String strategy = rule != null && rule.getChunkingStrategy() != null 
                            ? rule.getChunkingStrategy() : "SEMANTIC";

            List<TextSegment> segments = textChunkService.chunk(
                text, fileId, knowledgeBaseId, chunkSize, overlap, strategy
            );
            log.info("Text chunked into {} segments", segments.size());

            // 3. 向量化并存储每个分片
            List<Chunk> chunks = saveChunks(fileId, knowledgeBaseId, segments);

            log.info("File processing completed: fileId={}, chunks={}", fileId, chunks.size());
            return chunks;
        } catch (Exception e) {
            log.error("Failed to process file: fileId={}, error={}", fileId, e.getMessage(), e);
            throw new RuntimeException("文件处理失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存分片并向量化
     */
    private List<Chunk> saveChunks(Long fileId, Long knowledgeBaseId, List<TextSegment> segments) {
        List<Chunk> chunks = new ArrayList<>();

        for (int i = 0; i < segments.size(); i++) {
            TextSegment segment = segments.get(i);

            // 创建Chunk记录
            Chunk chunk = new Chunk();
            chunk.setFileId(fileId);
            chunk.setContent(segment.text());
            chunk.setChunkIndex(i);
            chunk.setMetadata(buildMetadata(segment));
            chunk.setDeleted(0);

            chunkMapper.insert(chunk);

            // 向量化并存储
            try {
                Embedding embedding = embeddingModel.embed(segment).content();
                String vectorId = IdGenerator.generateVectorId();
                embeddingStore.add(embedding, segment);

                chunk.setVectorId(vectorId);
                chunkMapper.updateById(chunk);

                log.debug("Chunk embedded: chunkId={}, vectorId={}", chunk.getId(), vectorId);
            } catch (Exception e) {
                log.error("Failed to embed chunk: chunkIndex={}, error={}", i, e.getMessage(), e);
                // 继续处理下一个，不中断整个流程
            }

            chunks.add(chunk);
        }

        log.info("Saved {} chunks for fileId={}", chunks.size(), fileId);
        return chunks;
    }

    /**
     * 将文本向量化（简单接口）
     */
    public String embed(String text) {
        TextSegment segment = TextSegment.from(text);
        Embedding embedding = embeddingModel.embed(segment).content();
        return embedding.vectorAsList().stream()
                .map(Object::toString)
                .collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * 向量相似度搜索
     */
    public List<RAGResultDTO.ChunkResultDTO> search(String query, List<Long> knowledgeBaseIds, 
                                                     int topK, double scoreThreshold) {
        log.info("Vector search: query={}, knowledgeBaseIds={}, topK={}, threshold={}",
            query, knowledgeBaseIds, topK, scoreThreshold);

        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("搜索查询不能为空");
        }

        try {
            // 将查询向量化
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 构建搜索请求
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(topK * 2)
                    .minScore(scoreThreshold)
                    .build();

            // 执行搜索
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            // 处理搜索结果
            List<RAGResultDTO.ChunkResultDTO> results = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                TextSegment segment = match.embedded();
                dev.langchain4j.data.document.Metadata segmentMetadata = segment.metadata();

                Long chunkId = null;
                Long fileId = null;
                try {
                    chunkId = segmentMetadata.getLong("chunkId");
                    fileId = segmentMetadata.getLong("fileId");
                } catch (Exception e) {
                    log.warn("Failed to get metadata: {}", e.getMessage());
                    continue;
                }

                if (chunkId == null || fileId == null) {
                    continue;
                }

                // 从MySQL获取完整内容
                Chunk chunk = chunkMapper.selectById(chunkId);
                if (chunk != null) {
                    RAGResultDTO.ChunkResultDTO result = new RAGResultDTO.ChunkResultDTO();
                    result.setChunkId(chunkId);
                    result.setFileId(fileId);
                    result.setContent(chunk.getContent());
                    result.setScore(match.score());
                    result.setMetadata(chunk.getMetadata());
                    results.add(result);
                }
            }

            // 按score排序并截取topK
            results.sort(Comparator.comparingDouble(RAGResultDTO.ChunkResultDTO::getScore).reversed());
            if (results.size() > topK) {
                results = results.subList(0, topK);
            }

            log.info("Vector search completed: {} results", results.size());
            return results;
        } catch (Exception e) {
            log.error("Vector search failed: {}", e.getMessage(), e);
            throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 删除文件的所有向量
     */
    @Transactional
    public void deleteChunksByFileId(Long fileId) {
        List<Chunk> chunks = chunkMapper.selectList(
            new LambdaQueryWrapper<Chunk>().eq(Chunk::getFileId, fileId)
        );

        for (Chunk chunk : chunks) {
            if (chunk.getVectorId() != null) {
                try {
                    embeddingStore.remove(chunk.getVectorId());
                } catch (Exception e) {
                    log.warn("Failed to remove embedding: vectorId={}", chunk.getVectorId(), e);
                }
            }
        }

        chunkMapper.delete(new LambdaQueryWrapper<Chunk>().eq(Chunk::getFileId, fileId));
        log.info("Deleted {} chunks for fileId={}", chunks.size(), fileId);
    }

    private String buildMetadata(TextSegment segment) {
        try {
            Metadata metadata = segment.metadata();
            if (metadata != null && !metadata.toMap().isEmpty()) {
                return com.alibaba.fastjson2.JSON.toJSONString(metadata.toMap());
            }
        } catch (Exception e) {
            log.warn("Failed to build metadata", e);
        }
        return "{}";
    }
}
