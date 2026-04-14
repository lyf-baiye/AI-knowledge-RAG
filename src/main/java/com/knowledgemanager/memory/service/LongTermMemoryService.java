package com.knowledgemanager.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.knowledgemanager.common.entity.UserMemory;
import com.knowledgemanager.common.mapper.UserMemoryMapper;
import com.knowledgemanager.common.util.IdGenerator;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * 长期记忆服务
 * 主动录入 + 向量化存储 + 语义相似度召回
 * 用于存储用户的重要信息、偏好、历史交互等
 */
@Slf4j
@Service
public class LongTermMemoryService {

    @Resource
    private UserMemoryMapper userMemoryMapper;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    /**
     * 提取并存储记忆（从对话中自动提取重要信息）
     */
    public void extractAndStoreMemories(Long userId, String query, String answer) {
        // 这里可以调用LLM提取重要信息，简化版本直接存储
        // 实际应该使用LLM判断哪些信息重要
        
        // 示例：存储用户的查询历史（可以过滤出重要的）
        if (query.length() > 10) { // 简单过滤：太短的问题不存储
            UserMemory memory = new UserMemory();
            memory.setUserId(userId);
            memory.setMemoryType("QUERY_HISTORY");
            memory.setContent(String.format("用户询问: %s", query));
            memory.setMetadata(String.format("{\"answer_preview\": \"%s\"}", 
                answer.length() > 100 ? answer.substring(0, 100) + "..." : answer));
            memory.setImportanceScore(calculateImportance(query));
            memory.setDeleted(0);

            userMemoryMapper.insert(memory);

            // 向量化存储
            try {
                storeMemoryVector(memory);
            } catch (Exception e) {
                log.error("Failed to store memory vector: {}", e.getMessage(), e);
            }
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
            log.info("Added long-term memory: userId={}, type={}", userId, type);
        } catch (Exception e) {
            log.error("Failed to store memory vector: {}", e.getMessage(), e);
        }
    }

    /**
     * 检索记忆 - 基于语义相似度
     */
    public List<String> retrieveMemories(Long userId, String query, int topK) {
        try {
            // 将查询向量化
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 搜索相似记忆
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(topK * 2)
                    .minScore(0.6)
                    .build();

            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();

            // 过滤并排序
            List<String> memories = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                Metadata metadata = match.embedded().metadata();
                Long memoryUserId = metadata.getLong("userId");
                
                if (memoryUserId != null && memoryUserId.equals(userId)) {
                    memories.add(match.embedded().text());
                }

                if (memories.size() >= topK) {
                    break;
                }
            }

            log.info("Retrieved {} long-term memories for userId={}", memories.size(), userId);
            return memories;
        } catch (Exception e) {
            log.error("Failed to retrieve memories: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 向量化存储单条记忆
     */
    private void storeMemoryVector(UserMemory memory) {
        HashMap<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("memoryId", memory.getId());
        metadataMap.put("userId", memory.getUserId());
        metadataMap.put("memoryType", memory.getMemoryType());
        metadataMap.put("importance", memory.getImportanceScore());

        Metadata docMetadata = new Metadata(metadataMap);
        TextSegment segment = TextSegment.from(memory.getContent(), docMetadata);
        Embedding embedding = embeddingModel.embed(segment).content();
        
        String vectorId = IdGenerator.generateVectorId();
        embeddingStore.add(embedding, segment);

        // 更新MySQL中的vectorId
        memory.setVectorId(vectorId);
        userMemoryMapper.updateById(memory);
    }

    /**
     * 计算重要性评分（简化版本）
     */
    private double calculateImportance(String content) {
        double score = 0.5; // 基础分

        // 长度越长可能越重要
        if (content.length() > 50) score += 0.1;
        if (content.length() > 100) score += 0.1;

        // 包含关键词可能更重要
        String[] importantKeywords = {"设计", "规范", "标准", "要求", "必须", "重要"};
        for (String keyword : importantKeywords) {
            if (content.contains(keyword)) {
                score += 0.1;
            }
        }

        return Math.min(score, 1.0);
    }

    /**
     * 获取用户的所有记忆
     */
    public List<UserMemory> getUserMemories(Long userId) {
        return userMemoryMapper.selectList(
            new LambdaQueryWrapper<UserMemory>()
                .eq(UserMemory::getUserId, userId)
                .eq(UserMemory::getDeleted, 0)
                .orderByDesc(UserMemory::getCreateTime)
        );
    }

    /**
     * 删除记忆
     */
    public void deleteMemory(Long memoryId, Long userId) {
        UserMemory memory = userMemoryMapper.selectById(memoryId);
        if (memory != null && memory.getUserId().equals(userId)) {
            memory.setDeleted(1);
            userMemoryMapper.updateById(memory);

            if (memory.getVectorId() != null) {
                try {
                    embeddingStore.remove(memory.getVectorId());
                } catch (Exception e) {
                    log.warn("Failed to remove memory vector: {}", e.getMessage());
                }
            }

            log.info("Deleted long-term memory: memoryId={}", memoryId);
        }
    }
}
