package com.markingsystem.exception;

/**
 * 限流异常。
 * 当用户超出每日额度（自动获取 / 手动刷新）时抛出，由 Controller 转换为 429 响应。
 */
public class RateLimitException extends RuntimeException {

    public RateLimitException(String message) {
        super(message);
    }
}
