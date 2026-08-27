package com.tkzou.miniforum.config;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * traceId 过滤器（P1-5 可观测性）
 * <p>
 * 入站生成/透传 <code>X-Trace-Id</code>，写入日志 MDC（对应 pattern 的 {@code %X{traceId:-}}），
 * 并在响应头回传——全链路（含 429/4xx 错误响应）可按 traceId 对账。
 * 透传优先：入站 traceId 合法（非空、≤64）则沿用（跨服务同链路复用同一 id），否则生成 32 位 hex。
 * <b>finally 清理 MDC</b>：防止请求线程复用池时 ThreadLocal 泄漏串号。
 */
@Component
public class TraceIdFilter implements Filter {

    /** 透传/回传请求头 */
    public static final String HEADER = "X-Trace-Id";
    /** MDC key（对应日志 pattern 的 %X{traceId}） */
    public static final String MDC_KEY = "traceId";
    /** 入站 traceId 最大长度（防超长污染/log injection） */
    private static final int MAX_LENGTH = 64;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String traceId = resolveTraceId(httpRequest);
        MDC.put(MDC_KEY, traceId);
        httpResponse.setHeader(HEADER, traceId); // 所有响应都带，便于前端/网关按 id 对账
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    /** 入站 X-Trace-Id 合法则透传，否则生成 32 位 hex（无横线） */
    private String resolveTraceId(HttpServletRequest request) {
        String inbound = request.getHeader(HEADER);
        if (inbound != null && !inbound.isBlank() && inbound.length() <= MAX_LENGTH) {
            return inbound.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
