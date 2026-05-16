package com.gdut.gxk.service;

/**
 * 缓存一致性服务接口
 * 确保Caffeine和Redis缓存的同步更新
 */
public interface CacheConsistencyService {

    /**
     * 清除指定键的所有缓存（Caffeine + Redis）
     * @param key 缓存键
     */
    void invalidateCache(String key);

    /**
     * 清除模式匹配的所有缓存
     * @param pattern 键模式
     */
    void invalidateCachePattern(String pattern);

    /**
     * 同步更新缓存（先更新数据库，再清除缓存）
     * @param key 缓存键
     * @param value 新值
     * @param timeoutSeconds 超时时间（秒）
     */
    void updateCache(String key, Object value, long timeoutSeconds);

    /**
     * 获取缓存统计信息
     * @return 统计信息
     */
    CacheStats getCacheStats();

    /**
     * 缓存统计信息
     */
    record CacheStats(
            long caffeineHits,
            long caffeineMisses,
            long redisHits,
            long redisMisses,
            double caffeineHitRate,
            double redisHitRate
    ) {}
}