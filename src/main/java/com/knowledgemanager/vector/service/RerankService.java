package com.knowledgemanager.vector.service;

import com.knowledgemanager.common.dto.RAGResultDTO;
import com.knowledgemanager.llm.service.LLMService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 结果重排序服务（Rerank）
 * 使用LLM对检索结果进行相关性重排序
 */
@Slf4j
@Service
public class RerankService {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Resource
    private LLMService llmService;

    @Value("${rerank.enabled:false}")
    private boolean rerankEnabled;

    @Value("${rerank.top-k:5}")
    private int rerankTopK;

    @Value("${rerank.method:score}")
    private String rerankMethod; // score: 基于分数, llm: 使用LLM重排序

    /**
     * 对检索结果进行重排序
     *
     * @param query 原始查询
     * @param results 检索结果列表
     * @param topK 返回数量
     * @return 重排序后的结果列表
     */
    public List<RAGResultDTO.ChunkResultDTO> rerank(String query, List<RAGResultDTO.ChunkResultDTO> results, int topK) {
        if (results == null || results.isEmpty()) {
            return results;
        }

        log.info("Reranking started: query={}, results={}, method={}", query, results.size(), rerankMethod);

        List<RAGResultDTO.ChunkResultDTO> rerankedResults;

        switch (rerankMethod.toLowerCase()) {
            case "llm":
                rerankedResults = rerankWithLLM(query, results, topK);
                break;
            case "keyword":
                rerankedResults = rerankWithKeyword(query, results, topK);
                break;
            case "score":
            default:
                rerankedResults = rerankWithScore(results, topK);
        }

        log.info("Reranking completed: {} results -> {} results", results.size(), rerankedResults.size());
        return rerankedResults;
    }

    /**
     * 使用LLM进行重排序（最准确但最慢）
     */
    private List<RAGResultDTO.ChunkResultDTO> rerankWithLLM(
            String query, 
            List<RAGResultDTO.ChunkResultDTO> results,
            int topK) {
        
        try {
            // 构建重排序prompt
            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("请根据以下查询对提供的文档片段进行相关性排序。\n\n");
            promptBuilder.append("查询：").append(query).append("\n\n");
            promptBuilder.append("文档片段：\n");

            for (int i = 0; i < results.size(); i++) {
                RAGResultDTO.ChunkResultDTO result = results.get(i);
                promptBuilder.append(String.format("[%d] %s\n", i + 1, result.getContent()));
            }

            promptBuilder.append("\n请按照与查询的相关性从高到低返回文档片段的ID列表（逗号分隔），只返回前").append(topK).append("个。\n");
            promptBuilder.append("返回格式示例：3,1,5,2,4");

            // 调用LLM
            String response = llmService.chat(promptBuilder.toString());
            
            // 解析LLM返回的排序结果
            List<RAGResultDTO.ChunkResultDTO> rerankedResults = parseRerankResult(response, results);
            
            // 调整分数（基于新排序）
            for (int i = 0; i < rerankedResults.size(); i++) {
                double newScore = 1.0 - (i * 0.05); // 从1.0递减
                rerankedResults.get(i).setScore(Math.max(0.5, newScore));
            }

            return rerankedResults.subList(0, Math.min(topK, rerankedResults.size()));
        } catch (Exception e) {
            log.error("LLM reranking failed, falling back to score-based reranking: {}", e.getMessage(), e);
            return rerankWithScore(results, topK);
        }
    }

    /**
     * 基于关键词匹配的重排序
     */
    private List<RAGResultDTO.ChunkResultDTO> rerankWithKeyword(
            String query, 
            List<RAGResultDTO.ChunkResultDTO> results,
            int topK) {
        
        // 提取查询中的关键词
        String[] queryTerms = query.toLowerCase().split("[\\s\\p{Punct}]+");
        
        List<RAGResultDTO.ChunkResultDTO> scoredResults = new ArrayList<>();
        for (RAGResultDTO.ChunkResultDTO result : results) {
            String content = result.getContent().toLowerCase();
            double originalScore = result.getScore();
            double keywordBoost = 0.0;

            // 计算关键词命中率和位置
            int totalMatches = 0;
            int exactMatches = 0;
            
            for (String term : queryTerms) {
                if (term.length() < 2) continue;
                
                int index = content.indexOf(term);
                if (index != -1) {
                    totalMatches++;
                    // 位置越靠前，权重越高
                    double positionWeight = 1.0 - (index / (double) content.length());
                    keywordBoost += 0.1 * positionWeight;
                    
                    // 完全匹配额外加分
                    if (content.equals(term)) {
                        exactMatches++;
                        keywordBoost += 0.2;
                    }
                }
            }

            // 关键词密度
            double keywordDensity = (double) totalMatches / queryTerms.length;
            keywordBoost *= keywordDensity;

            // 综合分数 = 原始分数 * 0.7 + 关键词 boost * 0.3
            double finalScore = originalScore * 0.7 + keywordBoost * 0.3;
            
            result.setScore(Math.min(1.0, finalScore));
            scoredResults.add(result);
        }

        // 按新分数排序
        scoredResults.sort(Comparator.comparingDouble(RAGResultDTO.ChunkResultDTO::getScore).reversed());
        
        return scoredResults.subList(0, Math.min(topK, scoredResults.size()));
    }

    /**
     * 基于原始分数的简单重排序
     */
    private List<RAGResultDTO.ChunkResultDTO> rerankWithScore(
            List<RAGResultDTO.ChunkResultDTO> results,
            int topK) {
        
        return results.stream()
                .sorted(Comparator.comparingDouble(RAGResultDTO.ChunkResultDTO::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * 解析LLM返回的重排序结果
     */
    private List<RAGResultDTO.ChunkResultDTO> parseRerankResult(
            String response, 
            List<RAGResultDTO.ChunkResultDTO> originalResults) {
        
        List<RAGResultDTO.ChunkResultDTO> rerankedResults = new ArrayList<>();
        
        try {
            String[] ids = response.split("[,\\s]+");
            for (String idStr : ids) {
                try {
                    int index = Integer.parseInt(idStr.trim()) - 1;
                    if (index >= 0 && index < originalResults.size()) {
                        rerankedResults.add(originalResults.get(index));
                    }
                } catch (NumberFormatException e) {
                    log.warn("Failed to parse rerank index: {}", idStr);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse rerank result: {}", e.getMessage());
            // 返回原始结果
            return new ArrayList<>(originalResults);
        }

        // 如果解析结果为空，返回原始结果
        if (rerankedResults.isEmpty()) {
            return new ArrayList<>(originalResults);
        }

        return rerankedResults;
    }
}
