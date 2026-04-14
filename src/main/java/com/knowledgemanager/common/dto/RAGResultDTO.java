package com.knowledgemanager.common.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class RAGResultDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String queryId;
    private String query;
    
    /**
     * 重写后的查询（基于上下文）
     */
    private String rewrittenQuery;
    
    /**
     * 检索到的知识片段
     */
    private List<ChunkResultDTO> results;
    
    /**
     * 长期记忆内容
     */
    private List<String> longTermMemories;
    
    /**
     * LLM生成的回答
     */
    private String answer;
    
    private Long responseTime;

    @Data
    public static class ChunkResultDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        private Long chunkId;
        private Long fileId;
        private String fileName;
        private String content;
        private Double score;
        private String metadata;
    }
}
