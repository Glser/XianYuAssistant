package com.feijimiao.xianyuassistant.exception;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常拦截器
 * 捕获所有异常并返回统一格式的错误信息
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Map<String, Object> handleBusinessException(BusinessException e) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", e.getCode());
        result.put("message", e.getMessage());
        return result;
    }
    
    /**
     * 处理验证码异常
     */
    @ExceptionHandler(CaptchaRequiredException.class)
    public Map<String, Object> handleCaptchaRequiredException(CaptchaRequiredException e) {
        Map<String, Object> result = new HashMap<>();
        if (isValidCaptchaUrl(e.getCaptchaUrl())) {
            result.put("code", 1001);
            result.put("message", e.getMessage());
            result.put("captchaUrl", e.getCaptchaUrl());
        } else {
            result.put("code", 1002);
            result.put("message", "闲鱼账号触发风控，暂未返回可打开的滑块验证链接。请稍后再试，避免重复请求。");
        }
        return result;
    }

    private boolean isValidCaptchaUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            java.net.URI uri = java.net.URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public Map<String, Object> handleException(Exception e) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", e.getMessage() != null ? e.getMessage() : "系统异常");
        return result;
    }
}
