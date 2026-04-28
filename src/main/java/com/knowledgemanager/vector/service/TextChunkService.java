package com.knowledgemanager.vector.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 语义分片服务
 * 句子级 embedding → 相邻余弦相似度 → 低于阈值切断 → 控制单块大小
 */
@Slf4j
@Service
public class TextChunkService {

    @Resource
    private EmbeddingModel embeddingModel;

    /** 中英文句子分隔符 */
    private static final Pattern SENTENCE_PATTERN = Pattern.compile(
        "(?<=[。！？.!?\\n])(?=[^。！？.!?\\n])"
    );

    /**
     * 语义相似度分片
     *
     * @param text              原始文本
     * @param fileId            文件ID
     * @param knowledgeBaseId   知识库ID
     * @param maxChunkSize      单块最大字符数
     * @param similarityThreshold  余弦相似度阈值（0~1），相邻句子低于此值则切断
     */
    public List<TextSegment> chunk(String text, Long fileId, Long knowledgeBaseId,
                                    int maxChunkSize, double similarityThreshold) {
        // 1. 切句子
        String[] rawSentences = SENTENCE_PATTERN.split(text);
        List<String> sentences = new ArrayList<>();
        for (String s : rawSentences) {
            String trimmed = s.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        log.info("Semantic chunking: {} sentences, maxChunkSize={}, threshold={}",
            sentences.size(), maxChunkSize, similarityThreshold);

        // 少于 2 句 → 不需要计算相似度，直接合并为一个块
        if (sentences.size() < 2) {
            List<TextSegment> result = new ArrayList<>();
            result.add(buildSegment(String.join("", sentences), fileId, knowledgeBaseId, 0));
            log.info("Single chunk ({} sentences → 1 chunk)", sentences.size());
            return result;
        }

        // 2. 为每个句子生成 embedding
        List<TextSegment> sentenceSegments = new ArrayList<>();
        for (String s : sentences) {
            sentenceSegments.add(TextSegment.from(s));
        }
        Response<List<Embedding>> response = embeddingModel.embedAll(sentenceSegments);
        List<Embedding> embeddings = response.content();

        // 3. 计算相邻句子余弦相似度
        double[] similarities = new double[sentences.size() - 1];
        for (int i = 0; i < similarities.length; i++) {
            similarities[i] = cosineSimilarity(
                embeddings.get(i).vectorAsList(),
                embeddings.get(i + 1).vectorAsList()
            );
        }

        // 4. 根据相似度 + 字数限制进行合并
        List<TextSegment> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder(sentences.get(0));
        int chunkIndex = 0;

        for (int i = 1; i < sentences.size(); i++) {
            String nextSentence = sentences.get(i);
            double sim = similarities[i - 1];
            int potentialSize = currentChunk.length() + nextSentence.length();

            if (sim >= similarityThreshold && potentialSize <= maxChunkSize) {
                // 语义接近且不超限 → 合并
                currentChunk.append(nextSentence);
            } else {
                // 语义不连续或会超限 → 保存当前块，开始新块
                chunks.add(buildSegment(currentChunk.toString(), fileId, knowledgeBaseId, chunkIndex++));
                currentChunk = new StringBuilder(nextSentence);
            }
        }

        // 最后一块
        if (currentChunk.length() > 0) {
            chunks.add(buildSegment(currentChunk.toString(), fileId, knowledgeBaseId, chunkIndex));
        }

        log.info("Semantic chunked: {} sentences → {} chunks (avg {} chars/chunk)",
            sentences.size(), chunks.size(),
            chunks.stream().mapToInt(s -> s.text().length()).average().orElse(0));
        return chunks;
    }

    private double cosineSimilarity(List<Float> a, List<Float> b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private TextSegment buildSegment(String text, Long fileId, Long knowledgeBaseId, int index) {
        Metadata metadata = new Metadata();
        metadata.put("chunkIndex", index);
        metadata.put("fileId", fileId);
        metadata.put("knowledgeBaseId", knowledgeBaseId);
        metadata.put("chunkSize", text.length());
        return TextSegment.from(text.trim(), metadata);
    }
}
