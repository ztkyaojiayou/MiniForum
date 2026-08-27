package com.tkzou.miniforum.recommend.prod.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.coldstart.AlphaBeta;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis Thompson 后验存储测试（P2-2，Mockito 不连真 Redis）
 */
class RedisNewItemPoolStoreTest {

    @Test
    void putIfAbsent_usesSetNxEx() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.set(anyString(), anyString(), any(SetParams.class))).thenReturn("OK");
        RedisNewItemPoolStore store = new RedisNewItemPoolStore(pool, 2592000, new ObjectMapper());

        assertTrue(store.putIfAbsent(100L, new AlphaBeta(), 60));
        verify(jedis).set(startsWith("coldstart:"), anyString(), any(SetParams.class));
    }

    @Test
    void get_deserializesJson() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.get(startsWith("coldstart:"))).thenReturn(
                "{\"alpha\":2.0,\"beta\":1.0,\"pendingExposures\":0}");
        RedisNewItemPoolStore store = new RedisNewItemPoolStore(pool, 2592000, new ObjectMapper());

        AlphaBeta ab = store.get(100L).orElseThrow();
        assertEquals(2.0, ab.getAlpha(), 1e-9);
    }

    @Test
    void containsKey_usesExists() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.exists(startsWith("coldstart:"))).thenReturn(true);
        RedisNewItemPoolStore store = new RedisNewItemPoolStore(pool, 2592000, new ObjectMapper());

        assertTrue(store.containsKey(100L));
    }
}
