package com.knowledgemanager.memory.service;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgemanager.common.entity.UserMemory;
import com.knowledgemanager.common.mapper.UserMemoryMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 长期记忆服务
 * 向量化存储到 MySQL (JSON) + 余弦相似度语义召回
 */
@Slf4j
@Service
public class LongTermMemoryService {

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private com.knowledgemanager.llm.service.LLMService llmService;

    /**
     * 从对话中提取重要信息并存储记忆
     */
    public void extractAndStoreMemories(Long userId, String query, String answer) {
        try {
            // 使用LLM提取重要信息
            String importantInfo = llmService.extractImportantInfo(query, answer);
            if (importantInfo == null || importantInfo.trim().isEmpty()) {
                return;
            }

            UserMemory memory = new UserMemory();
            memory.setUserId(userId);
            memory.setMemoryType("IMPORTANT_INFO");
            memory.setContent(importantInfo);
            memory.setMetadata(JSON.toJSONString(java.util.Map.of(
                "source_query", query,
                "source_answer", answer.length() > 200 ? answer.substring(0, 200) + "..." : answer
            )));
            memory.setImportanceScore(calculateImportance(importantInfo));
            memory.setDeleted(0);

            userMemoryMapper.insert(memory);
            storeMemoryVector(memory);
            log.info("Stored long-term memory: userId={}, memoryId={}", userId, memory.getId());
        } catch (Exception e) {
            log.error("Failed to extract and store memory: {}", e.getMessage(), e);
        }
    }

    /**
     * 手动添加记忆
     */
    public void addMemory(Long userId, String type, String content, Double importance) {
        UserMemory memory = new UserMemory();
        memory.setUserId(userId);
        memory.setMemoryType(type);
        memory.setContent(content);
        memory.setImportanceScore(importance);
        memory.setDeleted(0);

        userMemoryMapper.insert(memory);
        try {
            storeMemoryVector(memory);
        } catch (Exception e) {
            log.error("Failed to store memory vector: {}", e.getMessage(), e);
        }
    }

    /**
     * 语义相似度检索记忆
     */
    public List<String> retrieveMemories(Long userId, String query, int topK) {
        try {
            // 1. 向量化查询
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            List<Float> queryVector = queryEmbedding.vectorAsList();

            // 2. 拉取该用户所有记忆
            List<UserMemory> allMemories = userMemoryMapper.selectList(
                new LambdaQueryWrapper<UserMemory>()
                    .eq(UserMemory::getUserId, userId)
                    .eq(UserMemory::getDeleted, 0)
                    .isNotNull(UserMemory::getEmbedding)
            );

            if (allMemories.isEmpty()) {
                return new ArrayList<>();
            }

            // 3. 计算余弦相似度并排序
            List<MemoryScore> scored = new ArrayList<>();
            for (UserMemory memory : allMemories) {
                try {
                    List<Float> memoryVector = parseEmbedding(memory.getEmbedding());
                    if (memoryVector == null || memoryVector.size() != queryVector.size()) {
                        continue;
                    }
                    double similarity = cosineSimilarity(queryVector, memoryVector);
                    if (similarity >= 0.6) {
                        scored.add(new MemoryScore(memory.getContent(), similarity));
                    }
                } catch (Exception e) {
                    log.warn("Failed to compute similarity for memoryId={}: {}", memory.getId(), e.getMessage());
                }
            }

            scored.sort(Comparator.comparingDouble((MemoryScore s) -> s.score).reversed());

            List<String> results = new ArrayList<>();
            for (int i = 0; i < Math.min(topK, scored.size()); i++) {
                results.add(scored.get(i).content);
            }

            log.info("Retrieved {} long-term memories for userId={}", results.size(), userId);
            return results;
        } catch (Exception e) {
            log.error("Failed to retrieve memories: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 向量化并存入 MySQL embedding 列
     */
    private void storeMemoryVector(UserMemory memory) {
        Response<Embedding> response = embeddingModel.embed(memory.getContent());
        List<Float> vector = response.content().vectorAsList();
        memory.setEmbedding(JSON.toJSONString(vector));
        userMemoryMapper.updateById(memory);
    }

    /**
     * 计算余弦相似度
     */
    private double cosineSimilarity(List<Float> a, List<Float> b) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dotProduct += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        if (normA == 0 || normB == 0) {
            return 0.0;
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * 从 JSON 解析向量
     */
    private List<Float> parseEmbedding(String embeddingJson) {
        if (embeddingJson == null || embeddingJson.isEmpty()) {
            return null;
        }
        return JSON.parseArray(embeddingJson, Float.class);
    }

    /**
     * 计算重要性评分
     */
    private double calculateImportance(String content) {
        double score = 0.5;
        if (content.length() > 50) score += 0.1;
        if (content.length() > 100) score += 0.1;
        String[] importantKeywords = {"设计", "规范", "标准", "要求", "必须", "重要"};
        for (String keyword : importantKeywords) {
            if (content.contains(keyword)) {
                score += 0.1;
            }
        }
        return Math.min(score, 1.0);
    }

    public List<UserMemory> getUserMemories(Long userId) {
        return userMemoryMapper.selectList(
            new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getDeleted, 0)
                .orderByDesc(UserMemory::getCreateTime)
        );
    }

    public void deleteMemory(Long memoryId, Long userId) {
        UserMemory memory = userMemoryMapper.selectById(memoryId);
        if (memory != null && memory.getUserId().equals(userId)) {
            memory.setDeleted(1);
            userMemoryMapper.updateById(memory);
            log.info("Deleted long-term memory: memoryId={}", memoryId);
        }
    }

    private static class MemoryScore {
        String content;
        double score;

        MemoryScore(String content, double score) {
            this.content = content;
            this.score = score;
        }
    }
}
