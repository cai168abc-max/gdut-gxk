package com.gdut.gxk.exception;

import lombok.Getter;

/**
 * AI服务异常类
 * 用于封装AI调用过程中的各类异常，支持错误分类和降级处理
 * 
 * 异常类型包括：
 * - TIMEOUT: 请求超时
 * - RATE_LIMITED: 请求频率限制
 * - MODEL_ERROR: 模型返回错误
 * - NETWORK_ERROR: 网络连接错误
 * - AUTH_ERROR: 认证授权错误
 * - CONTENT_FILTERED: 内容被过滤
 * - SERVICE_UNAVAILABLE: 服务不可用
 * - UNKNOWN: 未知错误
 */
@Getter
public class AIException extends RuntimeException {

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /** 请求超时 */
        TIMEOUT("AI_REQUEST_TIMEOUT", "AI请求超时，请稍后重试"),
        /** 请求频率限制 */
        RATE_LIMITED("AI_RATE_LIMITED", "AI请求频率受限，请稍后重试"),
        /** 模型返回错误 */
        MODEL_ERROR("AI_MODEL_ERROR", "AI模型处理错误"),
        /** 网络连接错误 */
        NETWORK_ERROR("AI_NETWORK_ERROR", "AI服务网络连接失败"),
        /** 认证授权错误 */
        AUTH_ERROR("AI_AUTH_ERROR", "AI服务认证失败"),
        /** 内容被过滤 */
        CONTENT_FILTERED("AI_CONTENT_FILTERED", "内容被AI安全策略过滤"),
        /** 服务不可用 */
        SERVICE_UNAVAILABLE("AI_SERVICE_UNAVAILABLE", "AI服务暂时不可用"),
        /** 参数错误 */
        INVALID_PARAM("AI_INVALID_PARAM", "AI请求参数无效"),
        /** 上下文过长 */
        CONTEXT_TOO_LONG("AI_CONTEXT_TOO_LONG", "对话上下文过长"),
        /** 未知错误 */
        UNKNOWN("AI_UNKNOWN_ERROR", "AI服务未知错误");

        private final String code;
        private final String defaultMessage;

        ErrorType(String code, String defaultMessage) {
            this.code = code;
            this.defaultMessage = defaultMessage;
        }

        public String getCode() {
            return code;
        }

        public String getDefaultMessage() {
            return defaultMessage;
        }
    }

    /** 错误类型 */
    private final ErrorType errorType;
    
    /** 错误码 */
    private final String errorCode;
    
    /** 是否可重试 */
    private final boolean retryable;
    
    /** 原始异常 */
    private final Throwable cause;
    
    /** 重试次数 */
    private int retryCount = 0;

    /**
     * 构造函数 - 基础
     */
    public AIException(ErrorType errorType) {
        super(errorType.getDefaultMessage());
        this.errorType = errorType;
        this.errorCode = errorType.getCode();
        this.retryable = isRetryableByDefault(errorType);
        this.cause = null;
    }

    /**
     * 构造函数 - 自定义消息
     */
    public AIException(ErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.errorCode = errorType.getCode();
        this.retryable = isRetryableByDefault(errorType);
        this.cause = null;
    }

    /**
     * 构造函数 - 带原因
     */
    public AIException(ErrorType errorType, String message, Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.errorCode = errorType.getCode();
        this.retryable = isRetryableByDefault(errorType);
        this.cause = cause;
    }

    /**
     * 构造函数 - 完整参数
     */
    public AIException(ErrorType errorType, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.errorType = errorType;
        this.errorCode = errorType.getCode();
        this.retryable = retryable;
        this.cause = cause;
    }

    /**
     * 判断错误类型是否默认可重试
     */
    private static boolean isRetryableByDefault(ErrorType errorType) {
        return errorType == ErrorType.TIMEOUT ||
               errorType == ErrorType.RATE_LIMITED ||
               errorType == ErrorType.NETWORK_ERROR ||
               errorType == ErrorType.SERVICE_UNAVAILABLE;
    }

    /**
     * 设置重试次数
     */
    public AIException withRetryCount(int retryCount) {
        this.retryCount = retryCount;
        return this;
    }

    /**
     * 创建超时异常
     */
    public static AIException timeout(String message) {
        return new AIException(ErrorType.TIMEOUT, message);
    }

    /**
     * 创建超时异常 - 带原因
     */
    public static AIException timeout(String message, Throwable cause) {
        return new AIException(ErrorType.TIMEOUT, message, cause);
    }

    /**
     * 创建频率限制异常
     */
    public static AIException rateLimited(String message) {
        return new AIException(ErrorType.RATE_LIMITED, message);
    }

    /**
     * 创建模型错误异常
     */
    public static AIException modelError(String message) {
        return new AIException(ErrorType.MODEL_ERROR, message);
    }

    /**
     * 创建模型错误异常 - 带原因
     */
    public static AIException modelError(String message, Throwable cause) {
        return new AIException(ErrorType.MODEL_ERROR, message, cause);
    }

    /**
     * 创建网络错误异常
     */
    public static AIException networkError(String message, Throwable cause) {
        return new AIException(ErrorType.NETWORK_ERROR, message, cause);
    }

    /**
     * 创建认证错误异常
     */
    public static AIException authError(String message) {
        return new AIException(ErrorType.AUTH_ERROR, message);
    }

    /**
     * 创建内容过滤异常
     */
    public static AIException contentFiltered(String message) {
        return new AIException(ErrorType.CONTENT_FILTERED, message);
    }

    /**
     * 创建服务不可用异常
     */
    public static AIException serviceUnavailable(String message) {
        return new AIException(ErrorType.SERVICE_UNAVAILABLE, message);
    }

    /**
     * 创建服务不可用异常 - 带原因
     */
    public static AIException serviceUnavailable(String message, Throwable cause) {
        return new AIException(ErrorType.SERVICE_UNAVAILABLE, message, cause);
    }

    /**
     * 创建参数无效异常
     */
    public static AIException invalidParam(String message) {
        return new AIException(ErrorType.INVALID_PARAM, message);
    }

    /**
     * 创建上下文过长异常
     */
    public static AIException contextTooLong(String message) {
        return new AIException(ErrorType.CONTEXT_TOO_LONG, message);
    }

    /**
     * 创建未知错误异常
     */
    public static AIException unknown(String message, Throwable cause) {
        return new AIException(ErrorType.UNKNOWN, message, cause);
    }

    /**
     * 根据异常类型创建AIException
     * 自动识别常见异常类型并映射到对应的错误类型
     */
    public static AIException fromException(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            message = "未知错误";
        }
        
        String lowerMessage = message.toLowerCase();
        
        // 超时判断
        if (e instanceof java.net.SocketTimeoutException ||
            e instanceof java.util.concurrent.TimeoutException ||
            lowerMessage.contains("timeout") || lowerMessage.contains("timed out")) {
            return timeout("AI请求超时: " + message, e);
        }
        
        // 频率限制判断
        if (lowerMessage.contains("rate limit") || lowerMessage.contains("too many requests") ||
            lowerMessage.contains("429")) {
            return rateLimited("AI请求频率受限: " + message);
        }
        
        // 认证错误判断
        if (lowerMessage.contains("unauthorized") || lowerMessage.contains("authentication") ||
            lowerMessage.contains("api key") || lowerMessage.contains("401")) {
            return authError("AI服务认证失败: " + message);
        }
        
        // 内容过滤判断
        if (lowerMessage.contains("content filter") || lowerMessage.contains("safety") ||
            lowerMessage.contains("blocked") || lowerMessage.contains("filtered")) {
            return contentFiltered("内容被AI安全策略过滤: " + message);
        }
        
        // 服务不可用判断
        if (lowerMessage.contains("service unavailable") || lowerMessage.contains("503") ||
            lowerMessage.contains("overloaded")) {
            return serviceUnavailable("AI服务暂时不可用: " + message, e);
        }
        
        // 网络错误判断
        if (e instanceof java.net.ConnectException ||
            e instanceof java.net.UnknownHostException ||
            e instanceof java.net.SocketException ||
            e instanceof java.io.IOException) {
            return networkError("AI服务网络连接失败: " + message, e);
        }
        
        // 上下文过长判断
        if (lowerMessage.contains("context length") || lowerMessage.contains("token limit") ||
            lowerMessage.contains("maximum context")) {
            return contextTooLong("对话上下文过长: " + message);
        }
        
        // 默认返回模型错误
        return modelError("AI模型处理错误: " + message, e);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AIException{");
        sb.append("errorType=").append(errorType);
        sb.append(", errorCode='").append(errorCode).append('\'');
        sb.append(", retryable=").append(retryable);
        sb.append(", retryCount=").append(retryCount);
        sb.append(", message='").append(getMessage()).append('\'');
        if (cause != null) {
            sb.append(", cause=").append(cause.getClass().getSimpleName());
        }
        sb.append('}');
        return sb.toString();
    }
}
