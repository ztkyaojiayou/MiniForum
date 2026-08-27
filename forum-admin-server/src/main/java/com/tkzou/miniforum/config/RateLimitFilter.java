package com.tkzou.miniforum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 入口单机限流过滤器（P1-4）
 * <p>
 * 对 {@code /api/**}（排除 {@code /api/health}）按 IP 滑动窗口限流，超限返回 429 + Retry-After + 统一 JSON。
 * Filter 在 AuthInterceptor（HandlerInterceptor）之前执行 → 限流先于鉴权（429 优先于 401，"限流牺牲流量"）。
 * 限流器<b>懒建</b>：@Value 字段在构造后才注入，首次请求时才按配置实例化。
 * 单机限流仅单实例有效（对齐"聚合业务用单机限流，扩容友好"；多 Pod 需分布式限流，本批不做）。
 */
@Component
public class RateLimitFilter implements Filter {

    private final ObjectMapper objectMapper;

    @Value("${app.rec.rate-limit.enabled:true}")
    private boolean enabled;
    /** 每窗口每 key 放行上限（默认 100 次/60s/IP） */
    @Value("${app.rec.rate-limit.limit-per-window:100}")
    private int limitPerWindow;
    @Value("${app.rec.rate-limit.window-ms:60000}")
    private long windowMs;
    /** IP | IP_PATH（IP+路径，隔离单 IP 单路径突发，如爬虫只打 feed） */
    @Value("${app.rec.rate-limit.key-mode:IP}")
    private String keyMode;

    private volatile SlidingWindowRateLimiter limiter;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String uri = httpRequest.getRequestURI();
        if (!enabled || !uri.startsWith("/api/") || uri.startsWith("/api/health")) {
            chain.doFilter(request, response);
            return;
        }
        if (!limiter().tryAcquire(resolveKey(httpRequest))) {
            reject429(httpResponse);
            return;
        }
        chain.doFilter(request, response);
    }

    /** 懒建限流器：@Value 字段构造后才注入，首次请求时按配置实例化 */
    private SlidingWindowRateLimiter limiter() {
        SlidingWindowRateLimiter l = limiter;
        if (l == null) {
            synchronized (this) {
                l = limiter;
                if (l == null) {
                    l = new SlidingWindowRateLimiter(limitPerWindow, windowMs);
                    limiter = l;
                }
            }
        }
        return l;
    }

    /** 限流 key：默认按 IP；IP_PATH 时按 IP+路径 */
    private String resolveKey(HttpServletRequest request) {
        String ip = clientIp(request);
        return "IP_PATH".equalsIgnoreCase(keyMode) ? ip + "|" + request.getRequestURI() : ip;
    }

    /** 取客户端 IP：信任代理层时取 X-Forwarded-For 首个，否则取 remoteAddr */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void reject429(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(Math.max(1, windowMs / 1000)));
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.error(429, "请求过于频繁，请稍后再试"));
    }
}
