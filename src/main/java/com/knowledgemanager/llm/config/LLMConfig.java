package com.knowledgemanager.llm.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LLM配置 - 使用DeepSeek
 * DeepSeek兼容OpenAI API格式
 */
@Configuration
public class LLMConfig {

    @Value("${llm.deepseek.api-key:your-deepseek-api-key}")
    private String deepseekApiKey;

    @Value("${llm.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${llm.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${llm.deepseek.temperature:0.7}")
    private Double temperature;

    @Value("${llm.deepseek.max-tokens:2000}")
    private Integer maxTokens;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(deepseekApiKey)
                .modelName(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
    }
}
