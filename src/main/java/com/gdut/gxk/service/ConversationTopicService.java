package com.gdut.gxk.service;

/**
 * 对话主题追踪服务接口
 */
public interface ConversationTopicService {

    /**
     * 分析对话主题
     * @param conversationId 会话ID
     * @param message 最新消息
     * @return 主题关键词列表
     */
    java.util.List<String> analyzeTopic(String conversationId, String message);

    /**
     * 判断是否发生主题变化
     * @param conversationId 会话ID
     * @param newTopics 新主题关键词
     * @return 是否发生主题变化
     */
    boolean isTopicChanged(String conversationId, java.util.List<String> newTopics);

    /**
     * 更新对话主题
     * @param conversationId 会话ID
     * @param topics 主题关键词列表
     */
    void updateTopic(String conversationId, java.util.List<String> topics);

    /**
     * 获取当前对话主题
     * @param conversationId 会话ID
     * @return 当前主题关键词列表
     */
    java.util.List<String> getCurrentTopic(String conversationId);

    /**
     * 清理与当前主题无关的历史消息
     * @param conversationId 会话ID
     * @param relevantTopics 相关主题关键词
     * @return 清理后的消息数量
     */
    int cleanIrrelevantHistory(String conversationId, java.util.List<String> relevantTopics);

    /**
     * 重置对话主题
     * @param conversationId 会话ID
     */
    void resetTopic(String conversationId);
}