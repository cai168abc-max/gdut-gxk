package com.gdut.gxk.config;

import com.gdut.gxk.exception.AIException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * AI服务重试配置类
 * 提供完善的重试机制、降级策略和超时配置
 * 
 * 功能特性：
 * 1. 指数退避重试策略
 * 2. 可配置的重试次数和间隔
 * 3. 智能异常识别（仅对可重试异常进行重试）
 * 4. 降级响应策略
 * 5. 请求超时控制
 */
@Configuration
@EnableRetry
@Slf4j
public class AIRetryConfig {

    // ==================== 重试配置 ====================
    
    /** 最大重试次数 */
    @Value("${ai.retry.max-attempts:3}")
    private int maxRetryAttempts;
    
    /** 初始重试间隔（毫秒） */
    @Value("${ai.retry.initial-interval:1000}")
    private long initialRetryInterval;
    
    /** 重试间隔乘数（指数退避） */
    @Value("${ai.retry.multiplier:2.0}")
    private double retryMultiplier;
    
    /** 最大重试间隔（毫秒） */
    @Value("${ai.retry.max-interval:10000}")
    private long maxRetryInterval;

    // ==================== 超时配置 ====================
    
    /** AI请求连接超时（毫秒） */
    @Value("${ai.timeout.connect:5000}")
    private long connectTimeout;
    
    /** AI请求读取超时（毫秒） */
    @Value("${ai.timeout.read:60000}")
    private long readTimeout;
    
    /** AI请求写入超时（毫秒） */
    @Value("${ai.timeout.write:10000}")
    private long writeTimeout;
    
    /** 流式响应超时（毫秒） */
    @Value("${ai.timeout.streaming:300000}")
    private long streamingTimeout;

    // ==================== 降级配置 ====================
    
    /** 是否启用降级响应 */
    @Value("${ai.fallback.enabled:true}")
    private boolean fallbackEnabled;
    
    /** 降级响应消息 */
    @Value("${ai.fallback.message:抱歉，AI服务暂时繁忙，请稍后重试。}")
    private String fallbackMessage;

    /**
     * 配置RetryTemplate Bean
     * 使用指数退避策略和自定义异常分类
     */
    @Bean
    public RetryTemplate aiRetryTemplate() {
        log.info("初始化AI重试模板，最大重试次数: {}, 初始间隔: {}ms, 乘数: {}, 最大间隔: {}ms",
                maxRetryAttempts, initialRetryInterval, retryMultiplier, maxRetryInterval);
        
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // 配置指数退避策略
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialRetryInterval);
        backOffPolicy.setMultiplier(retryMultiplier);
        backOffPolicy.setMaxInterval(maxRetryInterval);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        // 配置重试策略 - 仅对可重试异常进行重试
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(AIException.class, true);
        retryableExceptions.put(java.net.SocketTimeoutException.class, true);
        retryableExceptions.put(java.net.ConnectException.class, true);
        retryableExceptions.put(java.io.IOException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(maxRetryAttempts, retryableExceptions);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        return retryTemplate;
    }

    /**
     * 配置超时执行器
     */
    @Bean
    public ScheduledExecutorService aiTimeoutExecutor() {
        return Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "ai-timeout-executor");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * AI服务重试执行器
     * 提供统一的重试执行入口
     */
    @Bean
    public AIRetryExecutor aiRetryExecutor(RetryTemplate aiRetryTemplate) {
        return new AIRetryExecutor(aiRetryTemplate, fallbackEnabled, fallbackMessage);
    }

    /**
     * 获取超时配置
     */
    public TimeoutConfig getTimeoutConfig() {
        return new TimeoutConfig(connectTimeout, readTimeout, writeTimeout, streamingTimeout);
    }

    /**
     * 获取降级配置
     */
    public FallbackConfig getFallbackConfig() {
        return new FallbackConfig(fallbackEnabled, fallbackMessage);
    }

    /**
     * 超时配置类
     */
    public record TimeoutConfig(long connectTimeout, long readTimeout, long writeTimeout, long streamingTimeout) {
        public long getConnectTimeout() { return connectTimeout; }
        public long getReadTimeout() { return readTimeout; }
        public long getWriteTimeout() { return writeTimeout; }
        public long getStreamingTimeout() { return streamingTimeout; }
    }

    /**
     * 降级配置类
     */
    public record FallbackConfig(boolean enabled, String message) {
        public boolean isEnabled() { return enabled; }
        public String getMessage() { return message; }
    }

    /**
     * AI重试执行器
     * 封装重试逻辑和降级处理
     */
    public static class AIRetryExecutor {
        
        private final RetryTemplate retryTemplate;
        private final boolean fallbackEnabled;
        private final String fallbackMessage;
        
        public AIRetryExecutor(RetryTemplate retryTemplate, boolean fallbackEnabled, String fallbackMessage) {
            this.retryTemplate = retryTemplate;
            this.fallbackEnabled = fallbackEnabled;
            this.fallbackMessage = fallbackMessage;
        }

        /**
         * 执行带重试的AI调用
         * 
         * @param action 要执行的操作
         * @param contextId 上下文ID（用于日志）
         * @param operationName 操作名称（用于日志）
         * @return 操作结果
         */
        public <T> T executeWithRetry(Supplier<T> action, String contextId, String operationName) {
            return executeWithRetry(action, contextId, operationName, null);
        }

        /**
         * 执行带重试的AI调用（支持自定义降级）
         * 
         * @param action 要执行的操作
         * @param contextId 上下文ID（用于日志）
         * @param operationName 操作名称（用于日志）
         * @param fallback 降级响应提供者
         * @return 操作结果
         */
        public <T> T executeWithRetry(Supplier<T> action, String contextId, String operationName, 
                                       Supplier<T> fallback) {
            final int[] attemptCount = {0};
            
            try {
                return retryTemplate.execute(context -> {
                    attemptCount[0] = context.getRetryCount() + 1;
                    
                    if (attemptCount[0] > 1) {
                        log.warn("[{}] {} 第{}次重试", contextId, operationName, attemptCount[0]);
                    }
                    
                    try {
                        return action.get();
                    } catch (Exception e) {
                        // 转换为AIException
                        AIException aiException = convertToAIException(e);
                        aiException.withRetryCount(attemptCount[0]);
                        
                        log.error("[{}] {} 失败，第{}次尝试: {}", 
                                contextId, operationName, attemptCount[0], aiException.getMessage());
                        
                        throw aiException;
                    }
                });
                
            } catch (AIException e) {
                log.error("[{}] {} 最终失败，已重试{}次: {}", 
                        contextId, operationName, e.getRetryCount(), e.getMessage());
                
                // 尝试降级
                if (fallbackEnabled && fallback != null) {
                    log.info("[{}] {} 启用降级响应", contextId, operationName);
                    return fallback.get();
                }
                
                throw e;
            } catch (Exception e) {
                log.error("[{}] {} 意外失败: {}", contextId, operationName, e.getMessage());
                
                // 尝试降级
                if (fallbackEnabled && fallback != null) {
                    log.info("[{}] {} 启用降级响应", contextId, operationName);
                    return fallback.get();
                }
                
                throw AIException.fromException(e);
            }
        }

        /**
         * 执行带超时和重试的AI调用
         * 
         * @param action 要执行的操作
         * @param contextId 上下文ID
         * @param operationName 操作名称
         * @param timeoutMillis 超时时间（毫秒）
         * @param fallback 降级响应
         * @return 操作结果
         */
        public <T> T executeWithTimeoutAndRetry(Supplier<T> action, String contextId, 
                                                 String operationName, long timeoutMillis,
                                                 Supplier<T> fallback) {
            return executeWithRetry(() -> {
                // 简单的超时检查（实际超时由HTTP客户端控制）
                long startTime = System.currentTimeMillis();
                T result = action.get();
                long elapsed = System.currentTimeMillis() - startTime;
                
                if (elapsed > timeoutMillis) {
                    log.warn("[{}] {} 执行耗时{}ms超过配置超时{}ms", 
                            contextId, operationName, elapsed, timeoutMillis);
                }
                
                return result;
            }, contextId, operationName, fallback);
        }

        /**
         * 将异常转换为AIException
         */
        private AIException convertToAIException(Exception e) {
            if (e instanceof AIException) {
                return (AIException) e;
            }
            return AIException.fromException(e);
        }

        /**
         * 获取默认降级消息
         */
        public String getFallbackMessage() {
            return fallbackMessage;
        }

        /**
         * 是否启用降级
         */
        public boolean isFallbackEnabled() {
            return fallbackEnabled;
        }
    }
}
