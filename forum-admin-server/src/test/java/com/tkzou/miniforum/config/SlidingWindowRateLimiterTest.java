package com.tkzou.miniforum.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 单机滑动窗口限流器测试（P1-4）
 * <p>
 * 窗口内达上限拒绝、时间推进滑出旧戳后放行、不同 key 独立计数、key 数统计。
 */
class SlidingWindowRateLimiterTest {

    @Test
    void rejectsWhenWindowLimitExceeded() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(3, 60_000);
        assertTrue(limiter.tryAcquire("ip-1"));
        assertTrue(limiter.tryAcquire("ip-1"));
        assertTrue(limiter.tryAcquire("ip-1"));
        assertFalse(limiter.tryAcquire("ip-1"), "窗口内第 4 次应拒绝");
        assertTrue(limiter.tryAcquire("ip-2"), "不同 key 独立计数");
    }

    @Test
    void slidesOutOldStampsAfterWindowPasses() {
        // 直接测 RateWindow（now 可控）：窗口 [now-windowMs, now]
        SlidingWindowRateLimiter.RateWindow window = new SlidingWindowRateLimiter.RateWindow();
        assertTrue(window.tryAcquire(1000, 2, 500));
        assertTrue(window.tryAcquire(1100, 2, 500));
        assertFalse(window.tryAcquire(1200, 2, 500), "窗口内已达 2 次");
        assertTrue(window.tryAcquire(1600, 2, 500), "now=1600 时 1000ms 的旧戳已滑出窗口 → 放行");
    }

    @Test
    void tracksKeyCount() {
        SlidingWindowRateLimiter limiter = new SlidingWindowRateLimiter(10, 60_000);
        limiter.tryAcquire("a");
        limiter.tryAcquire("b");
        limiter.tryAcquire("c");
        assertTrue(limiter.size() >= 3, "应跟踪已出现的 key");
    }
}
