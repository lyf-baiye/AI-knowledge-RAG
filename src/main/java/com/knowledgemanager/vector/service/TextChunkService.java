package com.knowledgemanager.vector.service;

import com.knowledgemanager.common.constant.Constants;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分片服务
 * 支持多种分片策略：固定大小、段落、句子、语义分块
 */
@Slf4j
@Service
public class TextChunkService {

    // 中英文句子分隔符
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[。！？.!?\\n]+");
    // 段落分隔符（匹配两个或更多换行符）
    private static final Pattern PARAGRAPH_PATTERN = Pattern.compile("\\n\\s*\\n");

    /**
     * 根据策略对文本进行分片
     *
     * @param text             原始文本
     * @param fileId           文件ID
     * @param knowledgeBaseId  知识库ID
     * @param chunkSize        分片大小
     * @param overlap          重叠大小
     * @param strategy         分片策略
     * @return 分片列表
     */
    public List<TextSegment> chunk(String text, Long fileId, Long knowledgeBaseId,
                                    int chunkSize, int overlap, String strategy) {
        log.info("Chunking text: length={}, strategy={}, chunkSize={}, overlap={}",
                text.length(), strategy, chunkSize, overlap);

        List<String> chunks;
        switch (strategy) {
            case Constants.ChunkStrategy.FIXED_SIZE:
                chunks = chunkByFixedSize(text, chunkSize, overlap);
                break;
            case Constants.ChunkStrategy.SEMANTIC:
                chunks = chunkBySemantic(text, chunkSize, overlap);
                break;
            case "PARAGRAPH":
                chunks = chunkByParagraph(text, chunkSize, overlap);
                break;
            case "SENTENCE":
                chunks = chunkBySentence(text, chunkSize, overlap);
                break;
            default:
                chunks = chunkByFixedSize(text, chunkSize, overlap);
        }

        // 为每个分片添加元数据
        List<TextSegment> segments = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            Metadata metadata = new Metadata();
            metadata.put("chunkIndex", i);
            metadata.put("fileId", fileId);
            metadata.put("knowledgeBaseId", knowledgeBaseId);
            metadata.put("chunkStrategy", strategy);
            metadata.put("chunkSize", chunks.get(i).length());

            TextSegment segment = TextSegment.from(chunks.get(i).trim(), metadata);
            segments.add(segment);
        }

        log.info("Text chunked into {} segments", segments.size());
        return segments;
    }

    /**
     * 固定大小分片（带重叠窗口）
     */
    private List<String> chunkByFixedSize(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end);
            chunks.add(chunk);

            start += (chunkSize - overlap);
        }

        return chunks;
    }

    /**
     * 语义分片（基于段落和标题等语义边界）
     */
    private List<String> chunkBySemantic(String text, int chunkSize, int overlap) {
        // 先按段落分割
        String[] paragraphs = PARAGRAPH_PATTERN.split(text);
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            // 如果当前段落超过chunkSize，进一步分割
            if (paragraph.length() > chunkSize) {
                // 先保存当前累积的chunk
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    currentChunk = new StringBuilder();
                }

                // 对长段落进行固定大小分割
                int start = 0;
                while (start < paragraph.length()) {
                    int end = Math.min(start + chunkSize, paragraph.length());
                    chunks.add(paragraph.substring(start, end));
                    start += (chunkSize - overlap);
                }
            } else if (currentChunk.length() + paragraph.length() <= chunkSize) {
                // 可以累积到当前chunk
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
            } else {
                // 当前chunk已满，保存并开始新的
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder(paragraph);
            }
        }

        // 添加最后一个chunk
        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    /**
     * 按段落分片
     */
    private List<String> chunkByParagraph(String text, int maxChunkSize, int overlap) {
        String[] paragraphs = PARAGRAPH_PATTERN.split(text);
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            if (currentChunk.length() + paragraph.length() <= maxChunkSize) {
                if (currentChunk.length() > 0) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
            } else {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString());
                    // 保留重叠部分
                    if (overlap > 0 && currentChunk.length() > overlap) {
                        String overlapText = currentChunk.substring(Math.max(0, currentChunk.length() - overlap));
                        currentChunk = new StringBuilder(overlapText);
                    } else {
                        currentChunk = new StringBuilder();
                    }
                }
                currentChunk.append(paragraph);
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    /**
     * 按句子分片
     */
    private List<String> chunkBySentence(String text, int maxChunkSize, int overlap) {
        String[] sentences = SENTENCE_PATTERN.split(text);
        List<String> chunks = new ArrayList<>();
        StringBuilder currentChunk = new StringBuilder();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            if (currentChunk.length() + sentence.length() <= maxChunkSize) {
                currentChunk.append(sentence).append(" ");
            } else {
                if (currentChunk.length() > 0) {
                    chunks.add(currentChunk.toString().trim());
                    // 保留重叠
                    if (overlap > 0 && currentChunk.length() > overlap) {
                        String overlapText = currentChunk.substring(Math.max(0, currentChunk.length() - overlap));
                        currentChunk = new StringBuilder(overlapText);
                    } else {
                        currentChunk = new StringBuilder();
                    }
                }
                currentChunk.append(sentence).append(" ");
            }
        }

        if (currentChunk.length() > 0) {
            chunks.add(currentChunk.toString().trim());
        }

        return chunks;
    }
}
