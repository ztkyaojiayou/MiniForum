package com.example.miniforum.exception;

/**
 * 未登录或登录过期异常
 * <p>
 * 由认证拦截器抛出，由全局异常处理器统一处理，返回 401。
 */
public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
