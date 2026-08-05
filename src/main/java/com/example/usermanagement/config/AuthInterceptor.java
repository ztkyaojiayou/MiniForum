package com.example.usermanagement.config;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * 登录认证拦截器
 * 拦截 /api/users/** 接口，未登录时返回 401
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String SESSION_USER = "userId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 放行预检请求（CORS）
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(SESSION_USER) == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"未登录或登录已过期\"}");
            return false;
        }
        return true;
    }
}
