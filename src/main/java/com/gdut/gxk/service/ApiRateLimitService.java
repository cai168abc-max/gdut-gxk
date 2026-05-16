package com.gdut.gxk.service;

/**
 * API限流服务接口
 * 用于控制AI服务调用频率，防止成本失控
 */
public interface ApiRateLimitService {

    /**
     * 检查是否允许调用
     * @param key 限流键（如用户ID、IP地址）
     * @return 是否允许调用
     */
    boolean tryAcquire(String key);

    /**
     * 检查是否允许调用（带配额类型）
     * @param key 限流键
     * @param quotaType 配额类型
     * @return 是否允许调用
     */
    boolean tryAcquire(String key, QuotaType quotaType);

    /**
     * 获取剩余配额
     * @param key 限流键
     * @param quotaType 配额类型
     * @return 剩余配额
     */
    long getRemainingQuota(String key, QuotaType quotaType);

    /**
     * 获取配额使用情况
     * @param key 限流键
     * @return 配额使用情况
     */
    QuotaUsage getQuotaUsage(String key);

    /**
     * 重置配额
     * @param key 限流键
     */
    void resetQuota(String key);

    /**
     * 配额类型
     */
    enum QuotaType {
        MINUTE,  // 每分钟限额
        DAILY    // 每日限额
    }

    /**
     * 配额使用情况
     */
    record QuotaUsage(
            long minuteUsed,
            long minuteLimit,
            long dailyUsed,
            long dailyLimit
    ) {}
}