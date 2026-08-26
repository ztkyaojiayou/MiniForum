package com.tkzou.miniforum.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ID 生成器单元测试
 * <p>
 * 覆盖：Snowflake 单调递增 / 跨 worker 唯一 / 并发唯一 / reset 下限；EntityIdProvider 委托实体生成器。
 */
class IdProviderTest {

    @Test
    void snowflake_shouldBeMonotonic() {
        SnowflakeIdProvider sf = new SnowflakeIdProvider(1);
        long prev = sf.next("Post");
        for (int i = 0; i < 10000; i++) {
            long cur = sf.next("Post");
            assertTrue(cur > prev, "Snowflake 应单调递增");
            prev = cur;
        }
    }

    @Test
    void snowflake_shouldBeUniqueAcrossWorkers() {
        SnowflakeIdProvider sf1 = new SnowflakeIdProvider(1);
        SnowflakeIdProvider sf2 = new SnowflakeIdProvider(2);
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            assertTrue(ids.add(sf1.next("Post")));
            assertTrue(ids.add(sf2.next("Post")));
        }
        assertEquals(10000, ids.size()); // worker 位不同 → 全局唯一
    }

    @Test
    void snowflake_shouldBeConcurrentUnique() throws Exception {
        SnowflakeIdProvider sf = new SnowflakeIdProvider(1);
        int threads = 8, perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        Set<Long> ids = ConcurrentHashMap.newKeySet();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < perThread; i++) {
                    ids.add(sf.next("Post"));
                }
                done.countDown();
            });
        }
        start.countDown();
        done.await(5, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(threads * perThread, ids.size()); // 并发无重复
    }

    @Test
    void reset_shouldSetLowerBound() {
        SnowflakeIdProvider sf = new SnowflakeIdProvider(1);
        sf.reset("Post", 1000L);
        assertTrue(sf.next("Post") >= 1000L); // 生成 ID 不低过 min
    }

    @Test
    void entityProvider_shouldDelegateToEntityGenerators() {
        EntityIdProvider ep = new EntityIdProvider();
        long a = ep.next("Post");
        long b = ep.next("Post");
        assertTrue(b > a); // 委托实体 AtomicLong，单调
        assertThrows(IllegalArgumentException.class, () -> ep.next("Unknown"));
    }
}
