package com.gdut.gxk.exception;

import com.gdut.gxk.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 全局异常处理器
 * 统一处理各种异常，返回标准化的错误响应
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理AI服务异常
     * 根据异常类型返回不同的HTTP状态码和错误信息
     * ⭐ 关键：隐藏技术细节，返回用户友好的错误提示
     */
    @ExceptionHandler(AIException.class)
    public Result<Object> handleAIException(AIException e) {
        log.error("AI服务异常: type={}, code={}, message={}, retryable={}, retryCount={}",
                e.getErrorType(), e.getErrorCode(), e.getMessage(), e.isRetryable(), e.getRetryCount());
        
        // 根据错误类型确定HTTP状态码
        HttpStatus status = determineHttpStatus(e.getErrorType());
        
        // ⭐ 用户友好的错误提示（隐藏技术细节）
        String userMessage = buildUserFriendlyMessage(e.getErrorType());
        
        // 构建详细的错误响应（仅记录日志，不暴露给用户）
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", e.getErrorType().name());
        errorDetails.put("errorCode", e.getErrorCode());
        errorDetails.put("retryable", e.isRetryable());
        
        // 对于可重试的错误，提供重试建议
        if (e.isRetryable()) {
            errorDetails.put("suggestion", "建议稍后重试");
        }
        
        // ⭐ 只返回友好消息给用户，技术细节记录在日志中
        return new Result<>(status.value(), userMessage, null);
    }
    
    /**
     * 构建用户友好的错误消息
     * 根据不同的错误类型返回不同的友好提示
     */
    private String buildUserFriendlyMessage(AIException.ErrorType errorType) {
        return switch (errorType) {
            case TIMEOUT -> "AI服务响应超时，请稍后重试";
            case RATE_LIMITED -> "当前AI服务调用频繁，请稍后再试";
            case AUTH_ERROR -> "AI服务认证失败，请联系管理员";
            case CONTENT_FILTERED -> "您的请求内容不符合规范，请调整后重试";
            case INVALID_PARAM -> "请求参数有误，请检查后重试";
            case CONTEXT_TOO_LONG -> "对话历史过长，请清除历史后重试";
            case SERVICE_UNAVAILABLE -> "AI服务暂时不可用，请稍后重试";
            case NETWORK_ERROR -> "网络连接异常，请检查网络后重试";
            case MODEL_ERROR -> "AI服务内部错误，请稍后重试";
            case UNKNOWN -> "AI服务异常，请稍后重试";
        };
    }
    
    /**
     * 根据AI错误类型确定HTTP状态码
     */
    private HttpStatus determineHttpStatus(AIException.ErrorType errorType) {
        return switch (errorType) {
            case TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;           // 504
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;    // 429
            case AUTH_ERROR -> HttpStatus.UNAUTHORIZED;           // 401
            case CONTENT_FILTERED -> HttpStatus.BAD_REQUEST;      // 400
            case INVALID_PARAM -> HttpStatus.BAD_REQUEST;         // 400
            case CONTEXT_TOO_LONG -> HttpStatus.REQUEST_ENTITY_TOO_LARGE; // 413
            case SERVICE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;   // 503
            case NETWORK_ERROR -> HttpStatus.BAD_GATEWAY;         // 502
            case MODEL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR; // 500
            case UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;     // 500
        };
    }

    /**
     * 处理参数校验异常（@Valid注解）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Object> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("参数校验失败：{}", e.getMessage());
        
        StringBuilder errorMsg = new StringBuilder("参数校验失败：");
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errorMsg.append(error.getField()).append(" ").append(error.getDefaultMessage()).append("; ");
        }
        
        return Result.badRequest(errorMsg.toString());
    }

    /**
     * 处理绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Object> handleBindException(BindException e) {
        log.warn("参数绑定失败：{}", e.getMessage());
        
        StringBuilder errorMsg = new StringBuilder("参数绑定失败：");
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            errorMsg.append(error.getField()).append(" ").append(error.getDefaultMessage()).append("; ");
        }
        
        return Result.badRequest(errorMsg.toString());
    }

    /**
     * 处理约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Object> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("约束违反异常：{}", e.getMessage());
        
        StringBuilder errorMsg = new StringBuilder("参数校验失败：");
        Set<ConstraintViolation<?>> violations = e.getConstraintViolations();
        for (ConstraintViolation<?> violation : violations) {
            errorMsg.append(violation.getPropertyPath()).append(" ").append(violation.getMessage()).append("; ");
        }
        
        return Result.badRequest(errorMsg.toString());
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Object> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("非法参数异常：{}", e.getMessage());
        return Result.badRequest(e.getMessage());
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleNullPointerException(NullPointerException e) {
        log.error("空指针异常", e);
        return Result.error("系统内部错误，请联系管理员");
    }

    /**
     * 处理运行时异常
     * ⭐ 隐藏技术细节，返回用户友好的错误提示
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        // 隐藏技术细节，只返回友好提示
        return Result.error("系统运行异常，请稍后重试");
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统异常，请联系管理员");
    }
}


