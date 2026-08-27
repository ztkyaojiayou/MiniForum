package com.tkzou.miniforum.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 入口单机限流过滤器测试（P1-4）
 * <p>
 * 非 /api 与 /api/health 放行、超限返回 429 + Retry-After + 统一 JSON（chain 不再调用）、
 * enabled=false 放行、X-Forwarded-For 按首个 IP 计数。
 */
class RateLimitFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RateLimitFilter filter = new RateLimitFilter(objectMapper);
    private final FilterChain chain = mock(FilterChain.class);
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        // 默认配置：启用，100 次/60s/IP
        ReflectionTestUtils.setField(filter, "enabled", true);
        ReflectionTestUtils.setField(filter, "limitPerWindow", 100);
        ReflectionTestUtils.setField(filter, "windowMs", 60_000L);
        ReflectionTestUtils.setField(filter, "keyMode", "IP");
    }

    @Test
    void nonApiPath_passesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void healthPath_passesThrough() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/health");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void overLimit_rejectsWith429() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/recommend/feed");
        ReflectionTestUtils.setField(filter, "limitPerWindow", 2);
        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain);
        filter.doFilter(request, response, chain); // 第 3 次超限

        verify(response).setStatus(429);
        verify(response).setHeader("Retry-After", "60");
        verify(chain, times(2)).doFilter(request, response); // 前两次放行，第 3 次被拒不再进 chain
    }

    @Test
    void disabled_passesThrough() throws Exception {
        ReflectionTestUtils.setField(filter, "enabled", false);
        when(request.getRequestURI()).thenReturn("/api/recommend/feed");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void xff_usesFirstIpForCounting() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/posts");
        when(request.getHeader("X-Forwarded-For")).thenReturn("1.2.3.4, 5.6.7.8");
        ReflectionTestUtils.setField(filter, "limitPerWindow", 1);
        filter.doFilter(request, response, chain); // 1.2.3.4 第一次，放行
        filter.doFilter(request, response, chain); // 1.2.3.4 第二次，超限 → 429
        verify(response).setStatus(429);
    }
}
