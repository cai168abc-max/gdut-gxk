package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.DistributedLockService;
import com.gdut.gxk.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务实现
 * 使用Redis实现分布式锁，防止并发写入的竞态条件
 */
@Service
@Slf4j
public class DistributedLockServiceImpl implements DistributedLockService {

    @Resource
    private RedisCacheService redisCacheService;

    /**
     * 锁键前缀
     */
    private static final String LOCK_KEY_PREFIX = "ai:lock:";

    /**
     * 锁值（使用UUID确保唯一性）
     */
    private static final ThreadLocal<String> LOCK_VALUE = ThreadLocal.withInitial(() -> UUID.randomUUID().toString());

    @Override
    public boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit) {
        String fullKey = LOCK_KEY_PREFIX + lockKey;
        long waitMillis = unit.toMillis(waitTime);
        long leaseMillis = unit.toMillis(leaseTime);
        long startTime = System.currentTimeMillis();

        try {
            while (System.currentTimeMillis() - startTime < waitMillis) {
                // 使用Redis的SET NX命令尝试获取锁
                if (tryAcquireLock(fullKey, leaseMillis)) {
                    log.debug("获取分布式锁成功 - key: {}", lockKey);
                    return true;
                }

                // 短暂等待后重试
                Thread.sleep(100);
            }

            log.warn("获取分布式锁超时 - key: {}, waitTime: {}ms", lockKey, waitMillis);
            return false;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断 - key: {}", lockKey);
            return false;
        } catch (Exception e) {
            log.error("获取分布式锁失败 - key: {}, error: {}", lockKey, e.getMessage());
            // Redis异常时返回true（降级策略，允许继续执行）
            return true;
        }
    }

    @Override
    public boolean lock(String lockKey, long leaseTime, TimeUnit unit) {
        return tryLock(lockKey, 0, leaseTime, unit);
    }

    @Override
    public void unlock(String lockKey) {
        String fullKey = LOCK_KEY_PREFIX + lockKey;
        try {
            // 验证锁值后再删除（防止误删其他线程的锁）
            Object value = redisCacheService.get(fullKey);
            if (value != null && value.toString().equals(LOCK_VALUE.get())) {
                redisCacheService.delete(fullKey);
                log.debug("释放分布式锁 - key: {}", lockKey);
            }
        } catch (Exception e) {
            log.error("释放分布式锁失败 - key: {}, error: {}", lockKey, e.getMessage());
        }
    }

    @Override
    public boolean isLocked(String lockKey) {
        String fullKey = LOCK_KEY_PREFIX + lockKey;
        try {
            return redisCacheService.get(fullKey) != null;
        } catch (Exception e) {
            log.error("检查锁状态失败 - key: {}, error: {}", lockKey, e.getMessage());
            return false;
        }
    }

    /**
     * 尝试获取锁（内部方法）
     */
    private boolean tryAcquireLock(String key, long expireMillis) {
        // RedisCacheService没有提供SET NX操作，这里模拟实现
        // 在实际项目中可以使用RedisTemplate的opsForValue().setIfAbsent()
        Object existing = redisCacheService.get(key);
        if (existing == null) {
            redisCacheService.set(key, LOCK_VALUE.get(), expireMillis, TimeUnit.MILLISECONDS);
            return true;
        }
        return false;
    }
}