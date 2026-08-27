package com.tkzou.miniforum.recommend.prod.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.coldstart.PostState;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Redis 流量池状态存储测试（P2-2，Mockito 不连真 Redis）
 */
class RedisTrafficPoolStoreTest {

    @Test
    void putIfAbsent_usesSetNxEx() throws Exception {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.set(anyString(), anyString(), any(SetParams.class))).thenReturn("OK");
        RedisTrafficPoolStore store = new RedisTrafficPoolStore(pool, 604800, new ObjectMapper());

        assertTrue(store.putIfAbsent(100L, new PostState(), 60));
        verify(jedis).set(startsWith("traffic:"), anyString(), any(SetParams.class));
    }

    @Test
    void putIfAbsent_returnsFalseWhenExists() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.set(anyString(), anyString(), any(SetParams.class))).thenReturn(null); // 已存在 → NX 失败
        RedisTrafficPoolStore store = new RedisTrafficPoolStore(pool, 604800, new ObjectMapper());

        assertFalse(store.putIfAbsent(100L, new PostState(), 60));
    }

    @Test
    void get_deserializesJson() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.get(startsWith("traffic:"))).thenReturn(
                "{\"tier\":1,\"exposures\":3,\"successes\":2,\"stopped\":false}");
        RedisTrafficPoolStore store = new RedisTrafficPoolStore(pool, 604800, new ObjectMapper());

        PostState state = store.get(100L).orElseThrow();
        assertFalse(state.isStopped());
        assertTrue(state.getTier() == 1);
    }
}
