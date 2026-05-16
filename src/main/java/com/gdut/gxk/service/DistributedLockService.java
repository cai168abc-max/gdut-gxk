package com.gdut.gxk.service;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁服务接口
 * 用于防止并发写入的竞态条件
 */
public interface DistributedLockService {

    /**
     * 获取锁
     * @param lockKey 锁键
     * @param waitTime 等待时间
     * @param leaseTime 持有时间
     * @param unit 时间单位
     * @return 是否获取成功
     */
    boolean tryLock(String lockKey, long waitTime, long leaseTime, TimeUnit unit);

    /**
     * 释放锁
     * @param lockKey 锁键
     */
    void unlock(String lockKey);

    /**
     * 获取锁（带超时）
     * @param lockKey 锁键
     * @param leaseTime 持有时间
     * @param unit 时间单位
     * @return 是否获取成功
     */
    boolean lock(String lockKey, long leaseTime, TimeUnit unit);

    /**
     * 检查锁是否被持有
     * @param lockKey 锁键
     * @return 是否被持有
     */
    boolean isLocked(String lockKey);
}