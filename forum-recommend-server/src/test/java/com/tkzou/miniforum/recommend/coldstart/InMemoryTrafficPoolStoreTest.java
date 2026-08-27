package com.tkzou.miniforum.recommend.coldstart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存流量池状态存储测试（P2-2）
 */
class InMemoryTrafficPoolStoreTest {

    @Test
    void putIfAbsent_deduplicates() {
        InMemoryTrafficPoolStore store = new InMemoryTrafficPoolStore();
        assertTrue(store.putIfAbsent(1L, new PostState(), 600), "首次应创建成功");
        assertFalse(store.putIfAbsent(1L, new PostState(), 600), "重复应返回 false（多 pod 去重）");
        assertEquals(1, store.size());
    }

    @Test
    void get_put_remove_size() {
        InMemoryTrafficPoolStore store = new InMemoryTrafficPoolStore();
        store.put(1L, new PostState());
        store.put(2L, new PostState());
        assertTrue(store.get(1L).isPresent(), "写入后应可读");
        assertEquals(2, store.size());
        store.remove(1L);
        assertTrue(store.get(1L).isEmpty());
        assertEquals(1, store.size());
    }

    @Test
    void all_returnsSnapshot() {
        InMemoryTrafficPoolStore store = new InMemoryTrafficPoolStore();
        store.put(1L, new PostState());
        store.put(2L, new PostState());
        assertEquals(2, store.all().size());
    }
}
