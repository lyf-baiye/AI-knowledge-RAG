package com.knowledgemanager.vector;

import com.knowledgemanager.vector.service.TextChunkService;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 文本分片服务测试
 */
class TextChunkServiceTest {

    private final TextChunkService textChunkService = new TextChunkService();

    @Test
    void testChunkByFixedSize() {
        // 测试固定大小分片
        String text = "这是一段测试文本".repeat(50);
        List<TextSegment> segments = textChunkService.chunk(
            text, 1L, 1L, 100, 10, "FIXED_SIZE"
        );
        
        assertNotNull(segments);
        assertFalse(segments.isEmpty());
        
        // 验证每个分片大小（考虑重叠）
        for (TextSegment segment : segments) {
            assertTrue(segment.text().length() <= 100);
        }
    }

    @Test
    void testChunkByParagraph() {
        // 测试段落分片
        String text = "第一段。\n\n第二段。\n\n第三段。\n\n第四段。";
        List<TextSegment> segments = textChunkService.chunk(
            text, 1L, 1L, 500, 0, "PARAGRAPH"
        );
        
        assertNotNull(segments);
        assertEquals(1, segments.size()); // 应该合并成1个分片
    }

    @Test
    void testChunkMetadata() {
        // 测试分片元数据
        String text = "测试内容";
        List<TextSegment> segments = textChunkService.chunk(
            text, 123L, 456L, 500, 0, "FIXED_SIZE"
        );
        
        assertEquals(1, segments.size());
        TextSegment segment = segments.get(0);
        
        assertEquals(123L, segment.metadata().getLong("fileId"));
        assertEquals(456L, segment.metadata().getLong("knowledgeBaseId"));
        assertEquals(0, segment.metadata().getInteger("chunkIndex"));
    }
}
