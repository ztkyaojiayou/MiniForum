package com.tkzou.miniforum.exception;

/**
 * 业务异常基类
 * 所有业务层面的异常都应继承此类，避免抛出通用 RuntimeException。
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
