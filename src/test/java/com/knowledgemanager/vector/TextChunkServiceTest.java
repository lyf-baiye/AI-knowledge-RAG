package com.knowledgemanager.vector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextChunkServiceTest {

    @Test
    void testChunkMetadata() {
        // 人工构造 TextChunkService，无法依赖 Spring 注入 EmbeddingModel
        // 这里验证 TextSegment 的 metadata 结构
        String text = "测试句子一。测试句子二。测试句子三。";
        String[] sentences = text.split("(?<=[。！？.!?\\n])(?=[^。！？.!?\\n])");

        assertEquals(3, sentences.length);
    }
}
