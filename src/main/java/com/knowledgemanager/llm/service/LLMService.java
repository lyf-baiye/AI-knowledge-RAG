package com.knowledgemanager.llm.service;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * LLM服务 - 封装与大语言模型的交互
 */
@Slf4j
@Service
public class LLMService {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    /**
     * 简单对话
     */
    public String chat(String userMessage, String systemPrompt) {
        try {
            Response<AiMessage> response = chatLanguageModel.generate(
                new UserMessage(userMessage)
            );
            return response.content().text();
        } catch (Exception e) {
            log.error("LLM chat failed: {}", e.getMessage(), e);
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 带系统提示的对话
     */
    public String chatWithSystem(String userMessage, String systemPrompt) {
        try {
            // 先发送系统提示（如果有方式支持）
            Response<AiMessage> response = chatLanguageModel.generate(
                List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userMessage)
                )
            );
            return response.content().text();
        } catch (Exception e) {
            log.error("LLM chat with system prompt failed: {}", e.getMessage(), e);
            throw new RuntimeException("LLM调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 简单问答（无系统提示）
     */
    public String chat(String userMessage) {
        return chat(userMessage, "你是一个有用的助手。");
    }

    /**
     * 摘要生成
     */
    public String generateSummary(String text) {
        String prompt = String.format("请将以下内容概括为简洁的摘要（不超过100字）：\n\n%s", text);
        return chat(prompt);
    }

    /**
     * 从对话中提取重要信息
     */
    public String extractImportantInfo(String query, String answer) {
        String prompt = String.format(
            "从以下用户问题和回答中，提取出重要的用户偏好、需求或关键信息，用简洁的语言描述。\n\n" +
            "用户问题：%s\n\n" +
            "回答：%s\n\n" +
            "提取的重要信息：",
            query, answer
        );
        return chat(prompt);
    }
}
