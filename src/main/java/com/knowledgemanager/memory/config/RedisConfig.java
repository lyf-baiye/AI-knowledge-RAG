package com.knowledgemanager.memory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * Redis配置类
 * 为短期记忆服务提供StringRedisTemplate Bean
 */
@Configuration
public class RedisConfig {

    /**
     * 配置StringRedisTemplate Bean
     * 用于短期记忆服务中的Redis操作
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        return template;
    }
}