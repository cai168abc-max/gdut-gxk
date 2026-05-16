package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.MessageSummaryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;

/**
 * 消息摘要服务实现
 * 使用AI生成对话历史摘要，压缩长对话以减少token消耗
 */
@Service
@Slf4j
public class MessageSummaryServiceImpl implements MessageSummaryService {

    @Resource
    private ChatClient chatClient;

    @Value("${spring.ai.chat.summary.threshold:15}")
    private int summaryThreshold;

    @Value("${spring.ai.chat.summary.enabled:true}")
    private boolean summaryEnabled;

    /**
     * 摘要生成提示词
     */
    private static final String SUMMARY_PROMPT = """
            请将以下对话历史总结为简洁的摘要，保留关键信息：
            
            要求：
            1. 摘要长度不超过100字
            2. 保留对话主题和关键问题
            3. 忽略无关细节
            
            对话历史：
            %s
            
            摘要：
            """;

    @Override
    public String generateSummary(List<Message> messages) {
        return generateSummary(messages, null);
    }

    @Override
    public String generateSummary(List<Message> messages, String customPrompt) {
        if (!summaryEnabled || messages == null || messages.isEmpty()) {
            return "";
        }

        try {
            // 构建对话历史文本
            StringBuilder historyBuilder = new StringBuilder();
            for (Message message : messages) {
                String role = switch (message.getMessageType().getValue()) {
                    case "user" -> "用户";
                    case "assistant" -> "助手";
                    case "system" -> "系统";
                    default -> "未知";
                };
                historyBuilder.append(role).append(": ").append(message.getText()).append("\n");
            }

            // 使用自定义提示词或默认提示词
            String prompt = (customPrompt != null && !customPrompt.isEmpty()) 
                    ? customPrompt.formatted(historyBuilder.toString())
                    : SUMMARY_PROMPT.formatted(historyBuilder.toString());

            // 调用AI生成摘要
            String summary = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("消息摘要生成成功 - 原消息数: {}, 摘要长度: {}", messages.size(), 
                    summary != null ? summary.length() : 0);
            return summary != null ? summary.trim() : "";

        } catch (Exception e) {
            log.error("生成消息摘要失败", e);
            return "";
        }
    }

    @Override
    public boolean needsSummary(List<Message> messages) {
        if (!summaryEnabled) {
            return false;
        }
        return messages != null && messages.size() >= summaryThreshold;
    }

    @Override
    public int getSummaryThreshold() {
        return summaryThreshold;
    }
}