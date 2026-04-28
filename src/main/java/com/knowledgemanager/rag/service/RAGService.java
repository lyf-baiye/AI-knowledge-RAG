package com.knowledgemanager.rag.service;

import com.knowledgemanager.common.dto.RAGQueryDTO;
import com.knowledgemanager.common.dto.RAGResultDTO;
import com.knowledgemanager.common.util.IdGenerator;
import com.knowledgemanager.memory.service.ShortTermMemoryService;
import com.knowledgemanager.memory.service.LongTermMemoryService;
import com.knowledgemanager.vector.service.HybridVectorService;
import com.knowledgemanager.vector.service.RerankService;
import com.knowledgemanager.llm.service.LLMService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class RAGService {

    @Resource
    private HybridVectorService hybridVectorService; // ✅ 使用原生混合检索服务

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Resource
    private ShortTermMemoryService shortTermMemoryService;

    @Resource
    private LongTermMemoryService longTermMemoryService;

    @Resource
    private LLMService llmService;

    @Resource
    private RerankService rerankService;

    @Value("${rag.system-prompt:你是一个专业的设计团队知识库助手。请基于以下检索到的设计知识内容来回答用户的问题。如果无法从提供的内容中找到答案，请说明你不知道，并建议用户查阅相关文档。}")
    private String systemPrompt;

    @Value("${rag.retrieval-top-k:5}")
    private int retrievalTopK;

    @Value("${rag.score-threshold:0.7}")
    private double scoreThreshold;

    @Value("${rag.conversation-rounds:5}")
    private int conversationRounds;

    /**
     * 完整的RAG查询流程：
     * 1. 查询重写（基于短期记忆上下文）
     * 2. 检索相关知识（向量搜索 + 长期记忆）
     * 3. 构建Prompt
     * 4. 调用LLM生成回答
     * 5. 更新记忆系统
     */
    public RAGResultDTO query(RAGQueryDTO queryDTO, Long userId, String sessionId) {
        log.info("RAG query started: userId={}, sessionId={}, query={}", userId, sessionId, queryDTO.getQuery());

        long startTime = System.currentTimeMillis();
        String queryId = IdGenerator.generateRagId();

        try {
            // 1. 查询重写 - 基于短期记忆上下文
            String rewrittenQuery = rewriteQuery(queryDTO.getQuery(), sessionId, userId);
            log.info("Rewritten query: {}", rewrittenQuery);

            // 2. 检索相关知识 - 混合检索（向量 + 长期记忆）
            List<RAGResultDTO.ChunkResultDTO> knowledgeChunks = retrieveKnowledge(
                rewrittenQuery, queryDTO.getKnowledgeBaseIds()
            );
            log.info("Retrieved {} knowledge chunks", knowledgeChunks.size());

            // 3. 检索长期记忆 - 用户历史偏好
            List<String> longTermMemories = longTermMemoryService.retrieveMemories(userId, rewrittenQuery, 3);
            log.info("Retrieved {} long-term memories", longTermMemories.size());

            // 4. 构建Prompt并调用LLM（对话历史按轮次召回，每轮 = user消息 + assistant消息）
            List<ShortTermMemoryService.MemoryMessage> shortTermMemory = shortTermMemoryService.getRecentMessages(userId, sessionId, conversationRounds * 2);
            String answer = generateAnswer(queryDTO.getQuery(), knowledgeChunks, longTermMemories, shortTermMemory);

            long responseTime = System.currentTimeMillis() - startTime;

            // 5. 更新短期记忆 (传入 userId)
            shortTermMemoryService.addMessage(userId, sessionId, "user", queryDTO.getQuery());
            shortTermMemoryService.addMessage(userId, sessionId, "assistant", answer);

            // 6. 提取重要信息到长期记忆
            longTermMemoryService.extractAndStoreMemories(userId, queryDTO.getQuery(), answer);

            RAGResultDTO result = new RAGResultDTO();
            result.setQueryId(queryId);
            result.setQuery(queryDTO.getQuery());
            result.setRewrittenQuery(rewrittenQuery);
            result.setResults(knowledgeChunks);
            result.setLongTermMemories(longTermMemories);
            result.setAnswer(answer);
            result.setResponseTime(responseTime);

            log.info("RAG query completed: queryId={}, time={}ms", queryId, responseTime);
            return result;
        } catch (Exception e) {
            log.error("RAG query failed: queryId={}, error={}", queryId, e.getMessage(), e);
            RAGResultDTO result = new RAGResultDTO();
            result.setQueryId(queryId);
            result.setQuery(queryDTO.getQuery());
            result.setAnswer("抱歉，处理您的查询时遇到了错误：" + e.getMessage());
            result.setResults(List.of());
            result.setResponseTime(System.currentTimeMillis() - startTime);
            return result;
        }
    }

    /**
     * 查询重写 - 基于对话上下文
     */
    private String rewriteQuery(String query, String sessionId, Long userId) {
        // 获取短期记忆（最近几条对话）
        List<ShortTermMemoryService.MemoryMessage> recentMessages = shortTermMemoryService.getRecentMessages(userId, sessionId, 5);
        
        if (recentMessages.isEmpty()) {
            return query;
        }

        // 使用LLM进行查询重写
        String rewritePrompt = String.format(
            "基于以下对话上下文，重写用户的最新问题，使其成为一个独立完整的问题，无需依赖上下文即可理解。\n\n" +
            "对话历史：\n%s\n\n" +
            "用户最新问题：%s\n\n" +
            "重写后的问题：",
            formatMessages(recentMessages),
            query
        );

        try {
            // 使用LLM进行查询重写
            Response<AiMessage> response = chatLanguageModel.generate(
                new dev.langchain4j.data.message.UserMessage(rewritePrompt)
            );
            String rewrittenQuery = response.content().text().trim();
            return rewrittenQuery.isEmpty() ? query : rewrittenQuery;
        } catch (Exception e) {
            log.warn("Query rewriting failed, using original query: {}", e.getMessage());
            return query;
        }
    }

    /**
     * 检索知识 - Pinecone 原生混合检索 (Dense + Sparse)
     */
    private List<RAGResultDTO.ChunkResultDTO> retrieveKnowledge(String query, List<Long> knowledgeBaseIds) {
        List<Map<String, Object>> rawResults = hybridVectorService.hybridSearch(query, retrievalTopK * 2);

        List<RAGResultDTO.ChunkResultDTO> knowledgeChunks = new ArrayList<>();
        for (Map<String, Object> raw : rawResults) {
            RAGResultDTO.ChunkResultDTO dto = new RAGResultDTO.ChunkResultDTO();
            dto.setChunkId(Long.valueOf(raw.get("id").toString()));
            dto.setScore(((Float) raw.get("score")).doubleValue());
            @SuppressWarnings("unchecked")
            Map<String, String> metadata = (Map<String, String>) raw.get("metadata");
            if (metadata != null) {
                dto.setContent(metadata.get("content"));
                dto.setFileName(metadata.get("fileName"));
            }
            knowledgeChunks.add(dto);
        }

        // 重排序
        knowledgeChunks = rerankService.rerank(query, knowledgeChunks, retrievalTopK);

        return knowledgeChunks;
    }

    /**
     * 生成回答 - 基于检索到的知识和记忆
     */
    private String generateAnswer(String originalQuery,
                                   List<RAGResultDTO.ChunkResultDTO> knowledgeChunks,
                                   List<String> longTermMemories,
                                   List<ShortTermMemoryService.MemoryMessage> shortTermMemory) {
        // 1. 构建系统提示：基础提示 + RAG知识 + 长期记忆
        StringBuilder systemPromptBuilder = new StringBuilder(systemPrompt);

        if (!knowledgeChunks.isEmpty()) {
            systemPromptBuilder.append("\n\n检索到的相关知识：\n");
            for (int i = 0; i < knowledgeChunks.size(); i++) {
                RAGResultDTO.ChunkResultDTO chunk = knowledgeChunks.get(i);
                systemPromptBuilder.append(String.format("[%d] %s\n", i + 1, chunk.getContent()));
            }
        }

        if (!longTermMemories.isEmpty()) {
            systemPromptBuilder.append("\n\n用户相关历史记忆：\n");
            for (int i = 0; i < longTermMemories.size(); i++) {
                systemPromptBuilder.append(String.format("[%d] %s\n", i + 1, longTermMemories.get(i)));
            }
        }

        systemPromptBuilder.append("\n请基于以上内容和对话历史回答用户的问题，确保回答准确、专业且相关。");

        // 2. 构建消息列表：SystemMessage + 对话历史 + 当前用户问题
        List<dev.langchain4j.data.message.ChatMessage> messages = new ArrayList<>();

        // SystemMessage: 系统提示 + RAG知识 + 长期记忆
        messages.add(new SystemMessage(systemPromptBuilder.toString()));

        // 对话历史（短期记忆）：不含最新一条用户消息，因为它就是当前问题
        for (ShortTermMemoryService.MemoryMessage msg : shortTermMemory) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AiMessage(msg.getContent()));
            }
        }

        // 当前用户问题（用原始问题，保留对话上下文中的指代关系）
        messages.add(new UserMessage(originalQuery));

        // 3. 调用LLM
        try {
            Response<AiMessage> response = chatLanguageModel.generate(messages);
            return response.content().text();
        } catch (Exception e) {
            log.error("LLM generation failed: {}", e.getMessage(), e);
            return "抱歉，我暂时无法回答您的问题，请稍后再试。";
        }
    }

    /**
     * 格式化消息
     */
    private String formatMessages(List<ShortTermMemoryService.MemoryMessage> messages) {
        return messages.stream()
                .map(m -> String.format("%s: %s", m.getRole(), m.getContent()))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 批量查询
     */
    public List<RAGResultDTO> batchQuery(List<RAGQueryDTO> queries, Long userId, String sessionId) {
        return queries.stream()
                .map(q -> query(q, userId, sessionId))
                .collect(Collectors.toList());
    }
}
