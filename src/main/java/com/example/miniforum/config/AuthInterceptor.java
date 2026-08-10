package com.example.miniforum.config;

import com.example.miniforum.exception.UnauthorizedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录认证拦截器
 * 拦截 /api/users/** 接口，未登录时抛出 {@link UnauthorizedException}，
 * 由全局异常处理器统一返回 401 响应，避免在拦截器中手动拼接 JSON。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String SESSION_USER = "userId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 放行预检请求（CORS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_USER) == null) {
            throw new UnauthorizedException("未登录或登录已过期");
        }
        return true;
    }
}
