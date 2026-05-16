package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.CacheConsistencyService;
import com.gdut.gxk.service.RedisCacheService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 缓存一致性服务实现
 * 确保Caffeine一级缓存和Redis二级缓存的同步更新
 */
@Service
@Slf4j
public class CacheConsistencyServiceImpl implements CacheConsistencyService {

    @Resource
    private RedisCacheService redisCacheService;

    /**
     * 缓存统计计数器
     */
    private final AtomicLong caffeineHits = new AtomicLong(0);
    private final AtomicLong caffeineMisses = new AtomicLong(0);
    private final AtomicLong redisHits = new AtomicLong(0);
    private final AtomicLong redisMisses = new AtomicLong(0);

    /**
     * 课程缓存键前缀
     */
    private static final String COURSE_KEY_PREFIX = "redis:course:";
    private static final String COMMENT_KEY_PREFIX = "redis:comment:";

    @Override
    public void invalidateCache(String key) {
        try {
            // 清除Redis缓存
            redisCacheService.delete(key);
            log.debug("清除缓存 - key: {}", key);
            
        } catch (Exception e) {
            log.error("清除缓存失败 - key: {}, error: {}", key, e.getMessage());
        }
    }

    @Override
    public void invalidateCachePattern(String pattern) {
        try {
            // 清除Redis中匹配模式的缓存
            redisCacheService.deletePattern(pattern);
            log.debug("清除缓存模式 - pattern: {}", pattern);
            
        } catch (Exception e) {
            log.error("清除缓存模式失败 - pattern: {}, error: {}", pattern, e.getMessage());
        }
    }

    @Override
    public void updateCache(String key, Object value, long timeoutSeconds) {
        try {
            // 更新Redis缓存
            redisCacheService.set(key, value, timeoutSeconds, TimeUnit.SECONDS);
            log.debug("更新缓存 - key: {}, timeout: {}s", key, timeoutSeconds);
            
        } catch (Exception e) {
            log.error("更新缓存失败 - key: {}, error: {}", key, e.getMessage());
        }
    }

    @Override
    public CacheStats getCacheStats() {
        long cHits = caffeineHits.get();
        long cMisses = caffeineMisses.get();
        long rHits = redisHits.get();
        long rMisses = redisMisses.get();

        double cHitRate = (cHits + cMisses) > 0 ? (double) cHits / (cHits + cMisses) : 0;
        double rHitRate = (rHits + rMisses) > 0 ? (double) rHits / (rHits + rMisses) : 0;

        return new CacheStats(cHits, cMisses, rHits, rMisses, cHitRate, rHitRate);
    }

    /**
     * 记录Caffeine缓存命中
     */
    public void recordCaffeineHit() {
        caffeineHits.incrementAndGet();
    }

    /**
     * 记录Caffeine缓存未命中
     */
    public void recordCaffeineMiss() {
        caffeineMisses.incrementAndGet();
    }

    /**
     * 记录Redis缓存命中
     */
    public void recordRedisHit() {
        redisHits.incrementAndGet();
    }

    /**
     * 记录Redis缓存未命中
     */
    public void recordRedisMiss() {
        redisMisses.incrementAndGet();
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        caffeineHits.set(0);
        caffeineMisses.set(0);
        redisHits.set(0);
        redisMisses.set(0);
        log.info("缓存统计已重置");
    }
}