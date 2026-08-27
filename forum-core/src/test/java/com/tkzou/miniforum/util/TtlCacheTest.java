package com.tkzou.miniforum.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * TtlCache 单元测试（P1-2 缓存防击穿组件）
 * <p>
 * 覆盖：TTL 内命中（loader 只调一次）、过期重载、并发同 key 单飞（loader 只执行一次）、
 * ttl=0 禁用、loader 异常不毒化缓存、invalidate、动态开关。
 */
class TtlCacheTest {

    @Test
    void get_returnsCachedValueWithinTtl() {
        TtlCache<String, String> cache = new TtlCache<>(60_000);
        AtomicInteger loads = new AtomicInteger();
        assertEquals("v", cache.get("k", () -> { loads.incrementAndGet(); return "v"; }));
        assertEquals("v", cache.get("k", () -> { loads.incrementAndGet(); return "v"; }));
        assertEquals(1, loads.get(), "TTL 内应只加载一次");
    }

    @Test
    void get_reloadsAfterTtlExpiry() throws InterruptedException {
        TtlCache<String, String> cache = new TtlCache<>(50);
        AtomicInteger loads = new AtomicInteger();
        cache.get("k", () -> { loads.incrementAndGet(); return "v1"; });
        Thread.sleep(80);
        cache.get("k", () -> { loads.incrementAndGet(); return "v2"; });
        assertEquals(2, loads.get(), "过期后应重新加载");
    }

    @Test
    void get_singleFlightOnConcurrentMiss() throws InterruptedException {
        TtlCache<String, String> cache = new TtlCache<>(60_000);
        AtomicInteger loads = new AtomicInteger();
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    cache.get("k", () -> {
                        loads.incrementAndGet();
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return "v";
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        assertEquals(1, loads.get(), "并发同 key miss 应单飞，loader 只执行一次");
    }

    @Test
    void get_disabledWhenTtlZero() {
        TtlCache<String, String> cache = new TtlCache<>(0);
        AtomicInteger loads = new AtomicInteger();
        cache.get("k", () -> { loads.incrementAndGet(); return "v"; });
        cache.get("k", () -> { loads.incrementAndGet(); return "v"; });
        assertEquals(2, loads.get(), "ttl=0 禁用，每次都现算");
    }

    @Test
    void loaderException_doesNotPoisonCache() {
        TtlCache<String, String> cache = new TtlCache<>(60_000);
        AtomicInteger loads = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> cache.get("k", () -> {
            loads.incrementAndGet();
            throw new IllegalStateException("boom");
        }));
        assertEquals("v", cache.get("k", () -> { loads.incrementAndGet(); return "v"; }));
        assertEquals(2, loads.get(), "异常不应毒化缓存，下次可重试");
    }

    @Test
    void invalidate_removesEntry() {
        TtlCache<String, String> cache = new TtlCache<>(60_000);
        AtomicInteger loads = new AtomicInteger();
        cache.get("k", () -> { loads.incrementAndGet(); return "v"; });
        cache.invalidate("k");
        cache.get("k", () -> { loads.incrementAndGet(); return "v"; });
        assertEquals(2, loads.get(), "invalidate 后应重新加载");
    }

    @Test
    void setTtl_zeroDisablesAndPositiveReenables() {
        TtlCache<String, String> cache = new TtlCache<>(60_000);
        AtomicInteger loads = new AtomicInteger();
        cache.get("k", () -> { loads.incrementAndGet(); return "v"; });
        cache.setTtlMillis(0);
        cache.get("k", () -> { loads.incrementAndGet(); return "v"; });
        assertEquals(2, loads.get(), "置 0 后禁用（每次现算）");
        cache.setTtlMillis(60_000);
        cache.invalidate("k"); // 清掉禁用期间残留的旧条目，让重新启用后首次是 miss
        cache.get("k", () -> { loads.incrementAndGet(); return "v"; });
        cache.get("k", () -> { loads.incrementAndGet(); return "v"; });
        assertEquals(3, loads.get(), "重新启用后首次 miss 重载、第二次命中");
    }
}
