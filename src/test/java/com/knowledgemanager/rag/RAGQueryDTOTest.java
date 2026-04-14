package com.knowledgemanager.rag;

import com.knowledgemanager.common.dto.RAGQueryDTO;
import com.knowledgemanager.common.dto.RAGResultDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RAG查询DTO测试
 */
class RAGQueryDTOTest {

    @Test
    void testRAGQueryDTO() {
        RAGQueryDTO dto = new RAGQueryDTO();
        dto.setQuery("测试问题");
        dto.setKnowledgeBaseIds(Arrays.asList(1L, 2L));
        dto.setTopK(5);
        dto.setScoreThreshold(0.7);
        
        assertEquals("测试问题", dto.getQuery());
        assertEquals(2, dto.getKnowledgeBaseIds().size());
        assertEquals(5, dto.getTopK());
        assertEquals(0.7, dto.getScoreThreshold());
    }

    @Test
    void testRAGResultDTO() {
        RAGResultDTO result = new RAGResultDTO();
        result.setQueryId("test-id");
        result.setQuery("测试问题");
        result.setAnswer("测试回答");
        
        RAGResultDTO.ChunkResultDTO chunk = new RAGResultDTO.ChunkResultDTO();
        chunk.setChunkId(1L);
        chunk.setContent("测试内容");
        chunk.setScore(0.9);
        
        result.setResults(List.of(chunk));
        
        assertEquals("test-id", result.getQueryId());
        assertEquals(1, result.getResults().size());
        assertEquals(0.9, result.getResults().get(0).getScore());
    }
}
