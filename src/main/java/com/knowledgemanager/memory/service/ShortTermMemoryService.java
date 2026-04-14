package com.knowledgemanager.memory.service;

import com.alibaba.fastjson2.JSON;
import com.knowledgemanager.llm.service.LLMService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 短期记忆服务 (基于 Redis)
 * 
 * 策略：
 * 1. Key = stm:{userId}:{sessionId}
 * 2. 存储结构：List of JSON objects { role, content }
 * 3. 阈值：32,000 字符 (非 token，工程上更简洁)
 * 4. 摘要触发：当总字符数 > 32k 时：
 *    - 提取"超出 32k 的部分" + "窗口内剩余的前一半" -> 发送给 LLM 生成摘要
 *    - 摘要生成后，只保留 [摘要文本] + [窗口内的后一半内容]
 */
@Slf4j
@Service
public class ShortTermMemoryService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Resource
    private LLMService llmService; // 用于生成摘要

    private static final int MAX_CHARS = 32000; // 32k 字符限制

    @Data
    public static class MemoryMessage {
        private String role;
        private String content;

        public MemoryMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    /**
     * 获取 Redis Key
     */
    private String getKey(Long userId, String sessionId) {
        return "stm:" + userId + ":" + sessionId;
    }

    /**
     * 添加消息并执行滑动窗口检查
     */
    public void addMessage(Long userId, String sessionId, String role, String content) {
        String key = getKey(userId, sessionId);
        MemoryMessage msg = new MemoryMessage(role, content);
        String json = JSON.toJSONString(msg);

        // 1. 写入 Redis List
        redisTemplate.opsForList().rightPush(key, json);
        redisTemplate.expire(key, 24, TimeUnit.HOURS);

        // 2. 检查是否需要摘要
        checkAndSummarize(key, userId, sessionId);
    }

    /**
     * 核心逻辑：检查字符数并触发摘要
     */
    private void checkAndSummarize(String key, Long userId, String sessionId) {
        List<String> allMessages = redisTemplate.opsForList().range(key, 0, -1);
        if (allMessages == null || allMessages.isEmpty()) return;

        // 计算总字符数
        int totalChars = 0;
        for (String msgJson : allMessages) {
            try {
                MemoryMessage msg = JSON.parseObject(msgJson, MemoryMessage.class);
                if (msg != null && msg.getContent() != null) {
                    totalChars += msg.getContent().length();
                }
            } catch (Exception ignored) {
            }
        }

        // 如果超过 32k，触发摘要逻辑
        if (totalChars > MAX_CHARS) {
            summarize(key, allMessages);
        }
    }

    /**
     * 执行摘要压缩
     * 策略：(超出 32k 的部分 + 窗口内前一半) -> 摘要
     * 结果保留：[摘要] + [窗口内后一半]
     */
    private void summarize(String key, List<String> allMessages) {
        try {
            // 按字符数精确计算分割点
            int totalCharCount = 0;
            int targetFirstHalfCharCount = 0;
            
            // 首先计算总字符数
            for (String json : allMessages) {
                try {
                    MemoryMessage msg = JSON.parseObject(json, MemoryMessage.class);
                    if (msg != null && msg.getContent() != null) {
                        totalCharCount += msg.getContent().length();
                    }
                } catch (Exception ignored) {}
            }
            
            // 计算超出部分 + 前一半的字符数
            int excessChars = totalCharCount - MAX_CHARS;
            targetFirstHalfCharCount = excessChars + (totalCharCount - excessChars) / 2;
            
            // 按字符数找到分割点
            int accumulatedChars = 0;
            int splitIndex = 0;
            for (int i = 0; i < allMessages.size(); i++) {
                try {
                    MemoryMessage msg = JSON.parseObject(allMessages.get(i), MemoryMessage.class);
                    if (msg != null && msg.getContent() != null) {
                        accumulatedChars += msg.getContent().length();
                    }
                } catch (Exception ignored) {}
                
                if (accumulatedChars >= targetFirstHalfCharCount) {
                    splitIndex = i + 1;
                    break;
                }
            }
            
            // 如果无法找到合适的分割点，则按原始方式处理
            if (splitIndex == 0) {
                splitIndex = allMessages.size() / 2;
            }
            
            List<String> firstHalf = allMessages.subList(0, splitIndex);
            List<String> secondHalf = allMessages.subList(splitIndex, allMessages.size());

            // 1. 构建需要摘要的文本 (超出部分 + 前一半)
            StringBuilder sb = new StringBuilder("请简要总结以下对话的核心内容：\n");
            for (String json : firstHalf) {
                try {
                    MemoryMessage msg = JSON.parseObject(json, MemoryMessage.class);
                    if (msg != null) {
                        sb.append(msg.getRole()).append(": ").append(msg.getContent()).append("\n");
                    }
                } catch (Exception ignored) {}
            }

            // 2. 调用 LLM 生成摘要
            String summary = llmService.generateSummary(sb.toString());
            log.info("Short-term memory summarized ({} chars -> summary)", sb.length());

            // 3. 构建新的 Redis List：[摘要消息] + [后一半消息]
            redisTemplate.delete(key); // 清除旧列表
            
            // 插入摘要
            MemoryMessage summaryMsg = new MemoryMessage("system", "【对话摘要】" + summary);
            redisTemplate.opsForList().rightPush(key, JSON.toJSONString(summaryMsg));

            // 插入后一半
            for (String json : secondHalf) {
                redisTemplate.opsForList().rightPush(key, json);
            }
            
            redisTemplate.expire(key, 24, TimeUnit.HOURS);

        } catch (Exception e) {
            log.error("Failed to summarize short-term memory: {}", e.getMessage(), e);
            // 失败时不做处理，防止数据丢失，下次重试
        }
    }

    /**
     * 获取最近的消息
     */
    public List<MemoryMessage> getRecentMessages(Long userId, String sessionId, int count) {
        String key = getKey(userId, sessionId);
        List<String> jsonList = redisTemplate.opsForList().range(key, -count, -1);
        return parseMessages(jsonList);
    }

    /**
     * 获取所有消息 (含摘要)
     */
    public List<MemoryMessage> getAllMessages(Long userId, String sessionId) {
        String key = getKey(userId, sessionId);
        List<String> jsonList = redisTemplate.opsForList().range(key, 0, -1);
        return parseMessages(jsonList);
    }

    private List<MemoryMessage> parseMessages(List<String> jsonList) {
        List<MemoryMessage> messages = new ArrayList<>();
        if (jsonList == null) return messages;
        
        for (String json : jsonList) {
            try {
                messages.add(JSON.parseObject(json, MemoryMessage.class));
            } catch (Exception e) {
                log.warn("Failed to parse memory message: {}", e.getMessage());
            }
        }
        return messages;
    }

    /**
     * 清空会话记忆
     */
    public void clearMemory(Long userId, String sessionId) {
        redisTemplate.delete(getKey(userId, sessionId));
    }
}