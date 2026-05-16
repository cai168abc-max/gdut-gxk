package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.ApiRateLimitService;
import com.gdut.gxk.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * API限流服务实现
 * 使用Redis实现分布式限流，支持每分钟和每日配额
 */
@Service
@Slf4j
public class ApiRateLimitServiceImpl implements ApiRateLimitService {

    @Resource
    private RedisCacheService redisCacheService;

    /**
     * 限流Key前缀
     */
    private static final String MINUTE_KEY_PREFIX = "ai:rate:limit:minute:";
    private static final String DAILY_KEY_PREFIX = "ai:rate:limit:daily:";

    /**
     * 默认配额配置
     */
    @Value("${ai.rate-limit.minute-limit:30}")
    private long minuteLimit;

    @Value("${ai.rate-limit.daily-limit:500}")
    private long dailyLimit;

    /**
     * 每分钟配额过期时间（秒）
     */
    private static final long MINUTE_EXPIRE_SECONDS = 60;

    /**
     * 每日配额过期时间（秒）- 24小时
     */
    private static final long DAILY_EXPIRE_SECONDS = 86400;

    @Override
    public boolean tryAcquire(String key) {
        return tryAcquire(key, QuotaType.MINUTE) && tryAcquire(key, QuotaType.DAILY);
    }

    @Override
    public boolean tryAcquire(String key, QuotaType quotaType) {
        String redisKey = getKey(key, quotaType);
        long limit = getLimit(quotaType);
        long expireSeconds = getExpireSeconds(quotaType);

        try {
            // 使用Redis原子操作INCR实现分布式限流
            // increment返回递增后的计数值
            long currentCount = redisCacheService.increment(redisKey, 1, expireSeconds, TimeUnit.SECONDS);

            if (currentCount == -1) {
                // Redis操作失败，降级策略：允许调用
                log.warn("Redis原子操作失败，允许调用作为降级策略 - key: {}", key);
                return true;
            }

            if (currentCount > limit) {
                log.warn("API限流触发 - key: {}, type: {}, count: {}, limit: {}", 
                        key, quotaType, currentCount, limit);
                return false;
            }

            log.debug("API调用允许 - key: {}, type: {}, count: {}", key, quotaType, currentCount);
            return true;

        } catch (Exception e) {
            log.error("限流检查失败 - key: {}, error: {}", key, e.getMessage());
            // Redis异常时允许调用（降级策略）
            return true;
        }
    }

    @Override
    public long getRemainingQuota(String key, QuotaType quotaType) {
        String redisKey = getKey(key, quotaType);
        long limit = getLimit(quotaType);

        try {
            Object countObj = redisCacheService.get(redisKey);
            long currentCount = countObj != null ? Long.parseLong(countObj.toString()) : 0;
            return Math.max(0, limit - currentCount);
        } catch (Exception e) {
            log.error("获取剩余配额失败 - key: {}, error: {}", key, e.getMessage());
            return limit;
        }
    }

    @Override
    public QuotaUsage getQuotaUsage(String key) {
        long minuteUsed = getCurrentCount(key, QuotaType.MINUTE);
        long dailyUsed = getCurrentCount(key, QuotaType.DAILY);
        
        return new QuotaUsage(minuteUsed, minuteLimit, dailyUsed, dailyLimit);
    }

    @Override
    public void resetQuota(String key) {
        redisCacheService.delete(MINUTE_KEY_PREFIX + key);
        redisCacheService.delete(DAILY_KEY_PREFIX + key);
        log.info("配额已重置 - key: {}", key);
    }

    /**
     * 获取当前计数
     */
    private long getCurrentCount(String key, QuotaType quotaType) {
        String redisKey = getKey(key, quotaType);
        try {
            Object countObj = redisCacheService.get(redisKey);
            return countObj != null ? Long.parseLong(countObj.toString()) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 获取Redis键
     */
    private String getKey(String key, QuotaType quotaType) {
        return switch (quotaType) {
            case MINUTE -> MINUTE_KEY_PREFIX + key;
            case DAILY -> DAILY_KEY_PREFIX + key;
        };
    }

    /**
     * 获取配额限制
     */
    private long getLimit(QuotaType quotaType) {
        return switch (quotaType) {
            case MINUTE -> minuteLimit;
            case DAILY -> dailyLimit;
        };
    }

    /**
     * 获取过期时间
     */
    private long getExpireSeconds(QuotaType quotaType) {
        return switch (quotaType) {
            case MINUTE -> MINUTE_EXPIRE_SECONDS;
            case DAILY -> DAILY_EXPIRE_SECONDS;
        };
    }
}