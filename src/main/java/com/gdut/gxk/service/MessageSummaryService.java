package com.gdut.gxk.service;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 消息摘要服务接口
 */
public interface MessageSummaryService {

    /**
     * 生成消息列表的摘要
     * @param messages 消息列表
     * @return 摘要内容
     */
    String generateSummary(List<Message> messages);

    /**
     * 生成消息摘要（带自定义提示词）
     * @param messages 消息列表
     * @param customPrompt 自定义提示词
     * @return 摘要内容
     */
    String generateSummary(List<Message> messages, String customPrompt);

    /**
     * 判断是否需要生成摘要（基于消息数量或token数）
     * @param messages 消息列表
     * @return 是否需要生成摘要
     */
    boolean needsSummary(List<Message> messages);

    /**
     * 获取摘要阈值配置
     * @return 摘要阈值
     */
    int getSummaryThreshold();
}