package com.tkzou.miniforum.idempotency;
import com.tkzou.miniforum.idempotency.impl.InMemoryIdempotencyStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存幂等存储单元测试
 * <p>
 * 覆盖：acquire 独占 / complete 后幂等返回 / release 释放 / 未知与 PROCESSING 不返回 / 过期可重新占位。
 */
class IdempotencyStoreTest {

    private InMemoryIdempotencyStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryIdempotencyStore(300000);
    }

    @Test
    void acquire_shouldBeExclusive() {
        assertTrue(store.acquire("k1"));
        assertFalse(store.acquire("k1")); // 已在处理
    }

    @Test
    void complete_shouldExposeCompletedPostId() {
        store.acquire("k1");
        store.complete("k1", 42L);
        assertEquals(42L, store.getCompleted("k1").orElseThrow());
        assertFalse(store.acquire("k1")); // 已完成，不可再占位
    }

    @Test
    void release_shouldAllowReacquire() {
        store.acquire("k1");
        store.release("k1");
        assertTrue(store.acquire("k1"));
    }

    @Test
    void getCompleted_shouldReturnEmptyForUnknownOrProcessing() {
        assertTrue(store.getCompleted("nope").isEmpty());
        store.acquire("k1");
        assertTrue(store.getCompleted("k1").isEmpty()); // PROCESSING 中不返回
    }

    @Test
    void expiredAcquire_shouldBeReacquirable() {
        InMemoryIdempotencyStore shortTtl = new InMemoryIdempotencyStore(-1); // 立即过期
        shortTtl.acquire("k1");
        assertTrue(shortTtl.acquire("k1")); // 过期后同 key 可重新占位
    }
}
