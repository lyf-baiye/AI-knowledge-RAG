package com.knowledgemanager.vector.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文本提取服务
 * 使用 Apache Tika 支持 PDF, Word, Markdown, TXT 等几乎所有主流格式
 */
@Slf4j
@Service
public class TextExtractionService {

    private final Tika tika = new Tika();

    /**
     * 从上传的文件中提取文本
     */
    public String extractText(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            String text = tika.parseToString(inputStream);
            log.info("Extracted {} characters from file: {}", text.length(), file.getOriginalFilename());
            return text;
        } catch (IOException | TikaException e) {
            log.error("Failed to extract text using Tika: {}", e.getMessage(), e);
            throw new RuntimeException("文本提取失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从字节数组中提取文本
     */
    public String extractTextFromBytes(byte[] content, String fileName) {
        try {
            String text = tika.parseToString(new java.io.ByteArrayInputStream(content));
            log.info("Extracted {} characters from byte content", text.length());
            return text;
        } catch (IOException | TikaException e) {
            log.error("Failed to extract text from bytes: {}", e.getMessage(), e);
            throw new RuntimeException("文本提取失败: " + e.getMessage(), e);
        }
    }
}