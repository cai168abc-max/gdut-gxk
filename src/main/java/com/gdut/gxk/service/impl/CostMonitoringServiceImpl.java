package com.gdut.gxk.service.impl;

import com.gdut.gxk.service.CostMonitoringService;
import com.gdut.gxk.service.RedisCacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 成本监控告警服务实现
 * 统计AI调用成本，设置多级告警阈值，防止成本失控
 */
@Service
@Slf4j
public class CostMonitoringServiceImpl implements CostMonitoringService {

    @Resource
    private RedisCacheService redisCacheService;

    /**
     * 统计键前缀
     */
    private static final String DAILY_COST_KEY_PREFIX = "ai:cost:daily:";
    private static final String DAILY_CALLS_KEY_PREFIX = "ai:calls:daily:";
    private static final String SESSION_COST_KEY_PREFIX = "ai:cost:session:";
    private static final String ALERT_HISTORY_KEY_PREFIX = "ai:alert:history:";

    /**
     * 阈值配置
     */
    @Value("${ai.cost.threshold.daily:100.0}")
    private double dailyCostThreshold;

    @Value("${ai.cost.threshold.max-calls:1000}")
    private long maxDailyCalls;

    /**
     * 多级告警阈值（百分比）
     */
    private static final double WARNING_THRESHOLD_PERCENT = 0.6;  // 60%
    private static final double CRITICAL_THRESHOLD_PERCENT = 0.8; // 80%
    private static final double EMERGENCY_THRESHOLD_PERCENT = 0.95; // 95%

    /**
     * 内存缓存今日统计（减少Redis访问）
     */
    private final AtomicLong todayCallCount = new AtomicLong(0);
    private final AtomicLong todayTokenCount = new AtomicLong(0);
    private volatile double todayCost = 0.0;

    /**
     * 当前日期（用于检测日期变化）
     */
    private volatile String currentDate = getTodayDate();

    /**
     * 是否已发送各级别告警（防止重复告警）
     */
    private volatile boolean warningAlertSent = false;
    private volatile boolean criticalAlertSent = false;
    private volatile boolean emergencyAlertSent = false;

    @Override
    public void recordCost(String sessionId, long tokensUsed, double cost) {
        // 检查日期变化
        checkDateChange();

        String dailyCostKey = DAILY_COST_KEY_PREFIX + currentDate;
        String dailyCallsKey = DAILY_CALLS_KEY_PREFIX + currentDate;
        String sessionCostKey = SESSION_COST_KEY_PREFIX + sessionId;

        try {
            // 更新内存统计
            todayCallCount.incrementAndGet();
            todayTokenCount.addAndGet(tokensUsed);
            todayCost += cost;

            // 更新Redis统计（使用原子操作）
            updateDailyCost(dailyCostKey, cost);
            updateDailyCalls(dailyCallsKey);
            updateSessionCost(sessionCostKey, cost);

            // 检查多级告警阈值
            checkMultiLevelAlerts();

            log.debug("记录AI调用成本 - sessionId: {}, tokens: {}, cost: {}元", sessionId, tokensUsed, cost);

        } catch (Exception e) {
            log.error("记录成本失败 - sessionId: {}, error: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 检查多级告警阈值并发送告警
     */
    private void checkMultiLevelAlerts() {
        double costPercent = todayCost / dailyCostThreshold;
        long callPercent = (todayCallCount.get() * 100) / maxDailyCalls;

        // 检查紧急告警（95%）
        if (!emergencyAlertSent && (costPercent >= EMERGENCY_THRESHOLD_PERCENT || callPercent >= 95)) {
            sendAlert(AlertLevel.EMERGENCY);
            emergencyAlertSent = true;
            criticalAlertSent = true;
            warningAlertSent = true;
        }
        // 检查严重告警（80%）
        else if (!criticalAlertSent && (costPercent >= CRITICAL_THRESHOLD_PERCENT || callPercent >= 80)) {
            sendAlert(AlertLevel.CRITICAL);
            criticalAlertSent = true;
            warningAlertSent = true;
        }
        // 检查警告告警（60%）
        else if (!warningAlertSent && (costPercent >= WARNING_THRESHOLD_PERCENT || callPercent >= 60)) {
            sendAlert(AlertLevel.WARNING);
            warningAlertSent = true;
        }
    }

    /**
     * 发送告警
     */
    private void sendAlert(AlertLevel level) {
        String message = buildAlertMessage(level);
        logAlert(level, message);
        
        // 记录告警历史到Redis
        recordAlertHistory(level, message);
        
        // TODO: 扩展支持邮件、短信等告警渠道
        // alertNotificationService.sendAlert(level, message);
    }

    /**
     * 构建告警消息
     */
    private String buildAlertMessage(AlertLevel level) {
        return String.format("[%s] AI成本告警 - 日成本: %.2f元/%.2f元, 调用次数: %d/%d",
                level.name(), todayCost, dailyCostThreshold, todayCallCount.get(), maxDailyCalls);
    }

    /**
     * 记录告警日志
     */
    private void logAlert(AlertLevel level, String message) {
        switch (level) {
            case WARNING:
                log.warn("🟡 {}", message);
                break;
            case CRITICAL:
                log.error("🟠 {}", message);
                break;
            case EMERGENCY:
                log.error("🔴 {}", message);
                break;
        }
    }

    /**
     * 记录告警历史到Redis
     */
    private void recordAlertHistory(AlertLevel level, String message) {
        String key = ALERT_HISTORY_KEY_PREFIX + currentDate;
        try {
            Object existing = redisCacheService.get(key);
            StringBuilder history = new StringBuilder(existing != null ? existing.toString() : "");
            if (history.length() > 0) {
                history.append("|");
            }
            history.append(System.currentTimeMillis()).append("|").append(level.name()).append("|").append(message);
            redisCacheService.set(key, history.toString(), 7, java.util.concurrent.TimeUnit.DAYS);
            log.debug("告警历史记录成功 - key: {}", key);
        } catch (Exception e) {
            log.error("记录告警历史失败", e);
        }
    }

    @Override
    public double getTodayTotalCost() {
        checkDateChange();
        return todayCost;
    }

    @Override
    public double getSessionCost(String sessionId) {
        String key = SESSION_COST_KEY_PREFIX + sessionId;
        try {
            Object value = redisCacheService.get(key);
            return value != null ? Double.parseDouble(value.toString()) : 0.0;
        } catch (Exception e) {
            log.error("获取会话成本失败 - sessionId: {}, error: {}", sessionId, e.getMessage());
            return 0.0;
        }
    }

    @Override
    public long getTodayCallCount() {
        checkDateChange();
        return todayCallCount.get();
    }

    @Override
    public boolean isOverThreshold() {
        return todayCost >= dailyCostThreshold || todayCallCount.get() >= maxDailyCalls;
    }

    @Override
    public AlertInfo getAlertInfo() {
        checkDateChange();
        
        boolean overThreshold = isOverThreshold();
        String message = "";
        
        if (todayCost >= dailyCostThreshold) {
            message = String.format("日成本%.2f元已超过阈值%.2f元", todayCost, dailyCostThreshold);
        } else if (todayCallCount.get() >= maxDailyCalls) {
            message = String.format("日调用次数%d已超过阈值%d", todayCallCount.get(), maxDailyCalls);
        }

        return new AlertInfo(overThreshold, todayCost, dailyCostThreshold, 
                todayCallCount.get(), maxDailyCalls, message);
    }

    @Override
    public void resetTodayStats() {
        todayCallCount.set(0);
        todayTokenCount.set(0);
        todayCost = 0.0;
        currentDate = getTodayDate();
        log.info("成本统计已重置");
    }

    /**
     * 定时任务：每天凌晨重置统计
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void dailyReset() {
        resetTodayStats();
        log.info("每日成本统计自动重置");
    }

    /**
     * 检查日期变化
     */
    private void checkDateChange() {
        String today = getTodayDate();
        if (!today.equals(currentDate)) {
            resetTodayStats();
        }
    }

    /**
     * 获取今日日期字符串
     */
    private String getTodayDate() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    /**
     * 更新每日成本
     */
    private void updateDailyCost(String key, double cost) {
        Object existing = redisCacheService.get(key);
        double current = existing != null ? Double.parseDouble(existing.toString()) : 0.0;
        redisCacheService.set(key, String.valueOf(current + cost), 2, TimeUnit.DAYS);
    }

    /**
     * 更新每日调用次数
     */
    private void updateDailyCalls(String key) {
        Object existing = redisCacheService.get(key);
        long current = existing != null ? Long.parseLong(existing.toString()) : 0;
        redisCacheService.set(key, String.valueOf(current + 1), 2, TimeUnit.DAYS);
    }

    /**
     * 更新会话成本
     */
    private void updateSessionCost(String key, double cost) {
        Object existing = redisCacheService.get(key);
        double current = existing != null ? Double.parseDouble(existing.toString()) : 0.0;
        redisCacheService.set(key, String.valueOf(current + cost), 1, TimeUnit.HOURS);
    }
}