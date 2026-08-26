/**
 * 全局异常处理（web 装配，admin）
 * <p>
 * {@link com.tkzou.miniforum.exception.GlobalExceptionHandler}（@RestControllerAdvice）
 * 统一捕获业务异常并映射 HTTP 状态码：400 业务/校验错误、401 未登录、404 不存在。
 * 自定义异常定义在 forum-core 的 exception 包；本包只放 web 层 handler（随 admin 模块提供给 demo）。
 */
package com.tkzou.miniforum.exception;
