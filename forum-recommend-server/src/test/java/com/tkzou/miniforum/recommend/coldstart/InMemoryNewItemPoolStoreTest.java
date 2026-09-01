package com.tkzou.miniforum.recommend.coldstart;
import com.tkzou.miniforum.recommend.coldstart.impl.InMemoryNewItemPoolStore;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 内存 Thompson 后验存储测试（P2-2）
 */
class InMemoryNewItemPoolStoreTest {

    @Test
    void putIfAbsent_deduplicates() {
        InMemoryNewItemPoolStore store = new InMemoryNewItemPoolStore();
        assertTrue(store.putIfAbsent(1L, new AlphaBeta(), 600), "首次应创建成功");
        assertFalse(store.putIfAbsent(1L, new AlphaBeta(), 600), "重复应返回 false");
    }

    @Test
    void get_put_contains_remove() {
        InMemoryNewItemPoolStore store = new InMemoryNewItemPoolStore();
        store.put(1L, new AlphaBeta(2.0, 1.0, 0));
        assertTrue(store.containsKey(1L));
        assertEquals(2.0, store.get(1L).orElseThrow().getAlpha(), 1e-9);
        store.remove(1L);
        assertFalse(store.containsKey(1L));
        assertTrue(store.get(1L).isEmpty());
    }
}
