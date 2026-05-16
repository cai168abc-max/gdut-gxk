package com.gdut.gxk.service;

/**
 * 成本监控告警服务接口
 * 用于统计AI调用成本并设置告警阈值
 */
public interface CostMonitoringService {

    /**
     * 记录AI调用成本
     * @param sessionId 会话ID
     * @param tokensUsed token消耗数量
     * @param cost 成本（元）
     */
    void recordCost(String sessionId, long tokensUsed, double cost);

    /**
     * 获取今日总消耗
     * @return 总消耗（元）
     */
    double getTodayTotalCost();

    /**
     * 获取会话累计消耗
     * @param sessionId 会话ID
     * @return 累计消耗（元）
     */
    double getSessionCost(String sessionId);

    /**
     * 获取今日调用次数
     * @return 调用次数
     */
    long getTodayCallCount();

    /**
     * 检查是否超过告警阈值
     * @return 是否超过阈值
     */
    boolean isOverThreshold();

    /**
     * 获取告警信息
     * @return 告警信息
     */
    AlertInfo getAlertInfo();

    /**
     * 重置今日统计
     */
    void resetTodayStats();

    /**
     * 告警信息
     */
    record AlertInfo(
            boolean overThreshold,
            double currentCost,
            double threshold,
            long callCount,
            long maxCalls,
            String message
    ) {}

    /**
     * 告警级别
     */
    enum AlertLevel {
        WARNING,    // 警告（60%阈值）
        CRITICAL,   // 严重（80%阈值）
        EMERGENCY   // 紧急（95%阈值）
    }
}