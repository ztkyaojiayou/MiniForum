package com.example.miniforum.exception;

/**
 * 登录凭证无效异常（用户名或密码错误）
 */
public class InvalidCredentialsException extends BusinessException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
