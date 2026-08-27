package com.tkzou.miniforum.recommend.prod.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ItemCF 模型 Redis 存取测试（P2-3，Mockito 不连真 Redis）
 */
class ItemCfModelRedisStoreTest {

    @Test
    void get_missingReturnsEmpty() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.get("itemcf:latest")).thenReturn(null);
        ItemCfModelRedisStore store = new ItemCfModelRedisStore(pool, new ObjectMapper());

        assertTrue(store.get().isEmpty());
    }

    @Test
    void publish_writesRedisKey() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        ItemCfModelRedisStore store = new ItemCfModelRedisStore(pool, new ObjectMapper());
        ItemCfModel model = new ItemCfModel();
        model.putSimilarities(1L, List.of(new ItemCfModel.SimilarItem(2L, 0.8)));

        store.publish(model);
        verify(jedis).set(eq("itemcf:latest"), anyString());
    }

    @Test
    void get_deserializesPublishedModel() {
        Jedis jedis = mock(Jedis.class);
        JedisPool pool = mock(JedisPool.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.get("itemcf:latest")).thenReturn("{\"1\":[{\"itemId\":2,\"similarity\":0.8}]}");
        ItemCfModelRedisStore store = new ItemCfModelRedisStore(pool, new ObjectMapper());

        Optional<ItemCfModel> loaded = store.get();
        assertTrue(loaded.isPresent());
        assertEquals(0.8, loaded.get().similarity(1L, 2L), 1e-9);
    }
}
