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
     */
    @ExceptionHandler(AIException.class)
    public Result<Object> handleAIException(AIException e) {
        log.error("AI服务异常: type={}, code={}, message={}, retryable={}, retryCount={}",
                e.getErrorType(), e.getErrorCode(), e.getMessage(), e.isRetryable(), e.getRetryCount());
        
        // 根据错误类型确定HTTP状态码
        HttpStatus status = determineHttpStatus(e.getErrorType());
        
        // 构建详细的错误响应
        Map<String, Object> errorDetails = new HashMap<>();
        errorDetails.put("errorType", e.getErrorType().name());
        errorDetails.put("errorCode", e.getErrorCode());
        errorDetails.put("retryable", e.isRetryable());
        errorDetails.put("retryCount", e.getRetryCount());
        
        // 对于可重试的错误，提供重试建议
        if (e.isRetryable()) {
            errorDetails.put("suggestion", "该错误通常可以通过重试解决，建议稍后重试");
        }
        
        return new Result<>(status.value(), e.getMessage(), errorDetails);
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
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Object> handleRuntimeException(RuntimeException e) {
        log.error("运行时异常", e);
        return Result.error("系统运行异常：" + e.getMessage());
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


