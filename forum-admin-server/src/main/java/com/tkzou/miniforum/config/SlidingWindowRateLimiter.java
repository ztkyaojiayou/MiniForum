package com.tkzou.miniforum.config;

import java.util.ArrayDeque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机滑动窗口限流器（P1-4，零依赖自研）
 * <p>
 * 每个 key（如 IP）维护一个时间戳队列（滑动窗口日志）：命中时先滑出窗口外旧戳，再判
 * {@code size >= limit} 则拒绝。同 key 用 {@code synchronized} 串行（窗口日志固有代价，限流本身极轻）。
 * 空窗即移除条目（带实例匹配），防止 map 无限增长。
 * <p>
 * 说明：仅单实例生效（进程内计数）；多 Pod 需网关/Redis 分布式限流（本批不做，注释留痕）。
 */
final class SlidingWindowRateLimiter {

    private final ConcurrentHashMap<String, RateWindow> windows = new ConcurrentHashMap<>();
    private final int limitPerWindow;
    private final long windowMs;

    SlidingWindowRateLimiter(int limitPerWindow, long windowMs) {
        this.limitPerWindow = limitPerWindow;
        this.windowMs = windowMs;
    }

    /** 尝试获取一个配额：窗口内未达上限则记录并放行，否则拒绝 */
    boolean tryAcquire(String key) {
        long now = System.currentTimeMillis();
        RateWindow window = windows.computeIfAbsent(key, k -> new RateWindow());
        synchronized (window) {
            boolean ok = window.tryAcquire(now, limitPerWindow, windowMs);
            if (window.isEmpty()) {
                windows.remove(key, window); // 空窗清理：带实例匹配防误删并发新窗
            }
            return ok;
        }
    }

    /** 当前跟踪的 key 数（测试/监控） */
    int size() {
        return windows.size();
    }

    /** 单 key 的滑动窗口：时间戳队列（FIFO） */
    static final class RateWindow {
        final ArrayDeque<Long> stamps = new ArrayDeque<>();

        boolean tryAcquire(long now, int limit, long windowMs) {
            long cutoff = now - windowMs;
            while (!stamps.isEmpty() && stamps.peekFirst() <= cutoff) {
                stamps.pollFirst(); // 滑出窗口外的旧时间戳
            }
            if (stamps.size() >= limit) {
                return false;
            }
            stamps.addLast(now);
            return true;
        }

        boolean isEmpty() {
            return stamps.isEmpty();
        }
    }
}
