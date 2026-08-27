package com.tkzou.miniforum.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Web 过滤器注册（P1-4/5）
 * <p>
 * 显式 {@link FilterRegistrationBean} 控制顺序：traceId 最前（所有响应都带 X-Trace-Id），
 * 限流其次（429 响应也带 traceId）。两者都先于 DispatcherServlet → 先于 AuthInterceptor（HandlerInterceptor）。
 * Filter 是 @Component，声明了 RegistrationBean 后 Boot 跳过自动注册，不会双跑。
 */
@Configuration
public class WebFilterConfig {

    @Bean
    public FilterRegistrationBean<TraceIdFilter> traceIdFilterRegistration(TraceIdFilter filter) {
        FilterRegistrationBean<TraceIdFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
