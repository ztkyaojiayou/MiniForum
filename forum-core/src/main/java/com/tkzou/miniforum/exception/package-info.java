/**
 * 业务异常体系
 * <p>
 * 自定义业务异常（BusinessException / ResourceNotFoundException / DuplicateUsernameException /
 * InvalidCredentialsException / UnauthorizedException）替代"抛通用 Exception"，由
 * admin 侧的 GlobalExceptionHandler（@RestControllerAdvice）统一映射为 HTTP 状态码：
 * 400 业务错误 / 401 未登录 / 404 不存在。
 * 本包是纯异常定义（共享域）；GlobalExceptionHandler 属 web 装配，在 forum-admin-server。
 */
package com.tkzou.miniforum.exception;
