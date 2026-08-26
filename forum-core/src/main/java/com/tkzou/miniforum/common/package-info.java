/**
 * 统一响应体与通用件
 * <p>
 * {@link com.tkzou.miniforum.common.Result}：所有接口统一返回 {@code {code, message, data}} 包装——
 * code=0 成功，非 0 业务/系统错误。前端 request() 统一解包（code≠0 抛错、401 跳登录）。
 * 属于共享域，admin/recommend/demo 三端共用。
 */
package com.tkzou.miniforum.common;
