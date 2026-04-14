package com.knowledgemanager.vector.config;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 向量服务配置
 * 主要负责初始化阿里云 DashScope 客户端（用于生成向量）
 */
@Configuration
public class VectorConfig {

    @Value("${langchain4j.dashscope.api-key}")
    private String dashscopeApiKey;

    /**
     * 初始化阿里云 DashScope 客户端
     * 用于调用 text-embedding-v3 模型获取 Dense 和 Sparse 向量
     */
    @Bean
    public TextEmbedding dashScopeClient() {
        return new TextEmbedding();
    }
    
    /**
     * 配置EmbeddingModel Bean
     * 用于长期记忆服务中的向量化操作
     * 使用阿里云DashScope的TextEmbedding客户端
     */
    @Bean
    public EmbeddingModel embeddingModel(@Value("${langchain4j.dashscope.api-key}") String dashscopeApiKey) {
        // 使用阿里云DashScope的embedding服务
        return new EmbeddingModel() {
            @Override
            public Response<Embedding> embed(TextSegment textSegment) {
                try {
                    TextEmbeddingParam param = TextEmbeddingParam.builder()
                            .apiKey(dashscopeApiKey)
                            .model("text-embedding-v3")
                            .text(textSegment.text())
                            .build();
                    
                    TextEmbedding textEmbedding = new TextEmbedding();
                    TextEmbeddingResult result = textEmbedding.call(param);
                    
                    if (result != null && result.getOutput() != null && 
                        result.getOutput().getEmbeddings() != null && 
                        !result.getOutput().getEmbeddings().isEmpty()) {
                        
                        TextEmbeddingResultItem item = 
                            result.getOutput().getEmbeddings().get(0);
                        
                        List<Float> vector = item.getEmbedding().stream()
                            .map(Double::floatValue)
                            .collect(Collectors.toList());
                        
                        Embedding embedding = Embedding.from(vector);
                        
                        return Response.from(embedding);
                    }
                    
                    throw new RuntimeException("Failed to generate embedding");
                } catch (Exception e) {
                    throw new RuntimeException("Failed to generate embedding: " + e.getMessage(), e);
                }
            }

            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
                try {
                    // 对每个文本段单独处理
                    List<Embedding> embeddings = textSegments.stream()
                        .map(this::embed)
                        .map(Response::content)
                        .collect(Collectors.toList());
                    
                    return Response.from(embeddings);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to generate embeddings: " + e.getMessage(), e);
                }
            }
        };
    }

    /**
     * 配置EmbeddingStore Bean
     * 用于长期记忆服务中的向量存储
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
}