package com.example.miniforum.exception;

/**
 * 用户名已存在异常
 */
public class DuplicateUsernameException extends BusinessException {

    public DuplicateUsernameException(String message) {
        super(message);
    }
}
