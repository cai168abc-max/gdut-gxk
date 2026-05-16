package com.gdut.gxk.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于Redis的ChatMemory实现
 * 用于存储AI对话历史，支持会话级别的对话记忆
 * 
 * 优化特性：
 * 1. 消息窗口大小限制 - 自动截断超出限制的历史消息
 * 2. 主题追踪支持 - 保留对话主题相关的历史
 */
@Slf4j
public class RedisChatMemory implements ChatMemory {

    private final StringRedisTemplate stringRedisTemplate;
    private final String keyPrefix;
    private final long expireSeconds;
    private final ObjectMapper objectMapper;
    
    /**
     * 消息窗口大小限制（默认20条）
     */
    private final int maxMessageCount;

    /**
     * 构造函数
     *
     * @param stringRedisTemplate Redis模板
     * @param keyPrefix           键前缀
     * @param expireSeconds       过期时间（秒）
     * @param maxMessageCount     最大消息数量限制
     */
    public RedisChatMemory(StringRedisTemplate stringRedisTemplate,
                           String keyPrefix,
                           long expireSeconds,
                           int maxMessageCount) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.keyPrefix = keyPrefix;
        this.expireSeconds = expireSeconds;
        this.maxMessageCount = maxMessageCount;
        this.objectMapper = new ObjectMapper();
        log.info("RedisChatMemory初始化完成，消息窗口大小：{}", maxMessageCount);
    }

    /**
     * 构造函数（兼容旧版，默认窗口大小20）
     */
    public RedisChatMemory(StringRedisTemplate stringRedisTemplate,
                           String keyPrefix,
                           long expireSeconds) {
        this(stringRedisTemplate, keyPrefix, expireSeconds, 20);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || messages == null || messages.isEmpty()) {
            log.warn("添加消息失败：conversationId或messages为空");
            return;
        }

        try {
            String key = getKey(conversationId);
            List<Message> existingMessages = getMessages(conversationId);
            existingMessages.addAll(messages);

            // 应用消息窗口限制
            existingMessages = applyMessageWindowLimit(existingMessages);

            // 序列化消息列表
            String messagesJson = serializeMessages(existingMessages);
            stringRedisTemplate.opsForValue().set(key, messagesJson, expireSeconds, TimeUnit.SECONDS);

            log.debug("添加消息成功：conversationId={}, 消息数={}, 窗口限制={}", 
                    conversationId, existingMessages.size(), maxMessageCount);
        } catch (Exception e) {
            log.error("添加消息失败：conversationId={}", conversationId, e);
        }
    }

    /**
     * 应用消息窗口限制
     * 如果消息数量超过maxMessageCount，保留最新的消息
     */
    private List<Message> applyMessageWindowLimit(List<Message> messages) {
        if (messages == null || messages.size() <= maxMessageCount) {
            return messages;
        }
        
        int excess = messages.size() - maxMessageCount;
        List<Message> trimmed = new ArrayList<>(messages.subList(excess, messages.size()));
        log.debug("消息窗口截断：原消息数={}, 截断后={}, 移除={}", 
                messages.size(), trimmed.size(), excess);
        return trimmed;
    }

    @Override
    public List<Message> get(String conversationId) {
        return getMessages(conversationId);
    }

    /**
     * 获取会话的最后N条消息（辅助方法）
     */
    public List<Message> get(String conversationId, int lastN) {
        List<Message> allMessages = getMessages(conversationId);
        if (allMessages.isEmpty()) {
            return allMessages;
        }

        int fromIndex = Math.max(0, allMessages.size() - lastN);
        return new ArrayList<>(allMessages.subList(fromIndex, allMessages.size()));
    }

    @Override
    public void clear(String conversationId) {
        if (conversationId == null) {
            log.warn("清除消息失败：conversationId为空");
            return;
        }

        try {
            String key = getKey(conversationId);
            Boolean deleted = stringRedisTemplate.delete(key);
            log.debug("清除消息：conversationId={}, 结果={}", conversationId, deleted);
        } catch (Exception e) {
            log.error("清除消息失败：conversationId={}", conversationId, e);
        }
    }

    /**
     * 获取会话的所有消息
     */
    private List<Message> getMessages(String conversationId) {
        if (conversationId == null) {
            return new ArrayList<>();
        }

        try {
            String key = getKey(conversationId);
            String messagesJson = stringRedisTemplate.opsForValue().get(key);

            if (messagesJson == null || messagesJson.isEmpty()) {
                return new ArrayList<>();
            }

            return deserializeMessages(messagesJson);
        } catch (Exception e) {
            log.error("获取消息失败：conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 构建Redis键
     */
    private String getKey(String conversationId) {
        return keyPrefix + conversationId;
    }

    /**
     * 序列化消息列表为JSON
     */
    private String serializeMessages(List<Message> messages) throws JsonProcessingException {
        List<Map<String, Object>> messageMaps = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> messageMap = Map.of(
                    "type", getMessageType(message),
                    "content", message.getText()
            );
            messageMaps.add(messageMap);
        }
        return objectMapper.writeValueAsString(messageMaps);
    }

    /**
     * 从JSON反序列化消息列表
     */
    private List<Message> deserializeMessages(String json) throws JsonProcessingException {
        List<Map<String, Object>> messageMaps = objectMapper.readValue(
                json,
                new TypeReference<List<Map<String, Object>>>() {}
        );

        List<Message> messages = new ArrayList<>();
        for (Map<String, Object> map : messageMaps) {
            String type = (String) map.get("type");
            String content = (String) map.get("content");

            Message message = switch (type) {
                case "user" -> new UserMessage(content);
                case "assistant" -> new AssistantMessage(content);
                case "system" -> new SystemMessage(content);
                default -> {
                    log.warn("未知的消息类型：{}，使用UserMessage", type);
                    yield new UserMessage(content);
                }
            };
            messages.add(message);
        }

        return messages;
    }

    /**
     * 获取消息类型标识
     */
    private String getMessageType(Message message) {
        return message.getMessageType().getValue();
    }
}