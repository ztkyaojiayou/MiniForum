package com.tkzou.miniforum.config;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * traceId 过滤器测试（P1-5）
 * <p>
 * 无入站头生成 / 入站头透传 / doFilter 期间 MDC 有值且 finally 清理 / 超长头重新生成。
 */
class TraceIdFilterTest {

    private final TraceIdFilter filter = new TraceIdFilter();

    @Test
    void generatesTraceIdWhenNoInboundHeader() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(TraceIdFilter.HEADER)).thenReturn(null);
        AtomicReference<String> captured = new AtomicReference<>();
        filter.doFilter(request, response, (req, res) -> captured.set(MDC.get(TraceIdFilter.MDC_KEY)));
        assertNotNull(captured.get(), "doFilter 期间 MDC 应有 traceId");
        verify(response).setHeader(eq(TraceIdFilter.HEADER), anyString());
    }

    @Test
    void propagatesInboundTraceId() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(TraceIdFilter.HEADER)).thenReturn("abc-123");
        filter.doFilter(request, response, (req, res) -> {});
        verify(response).setHeader(TraceIdFilter.HEADER, "abc-123");
    }

    @Test
    void clearsMdcAfterDoFilter() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(TraceIdFilter.HEADER)).thenReturn(null);
        filter.doFilter(request, response, (req, res) -> {});
        assertNull(MDC.get(TraceIdFilter.MDC_KEY), "doFilter 返回后应清理 MDC（防线程池复用串号）");
    }

    @Test
    void regeneratesWhenInboundHeaderTooLong() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader(TraceIdFilter.HEADER)).thenReturn("x".repeat(100)); // 超 64，防注入
        filter.doFilter(request, response, (req, res) -> {});
        verify(response, never()).setHeader(TraceIdFilter.HEADER, "x".repeat(100));
    }
}
