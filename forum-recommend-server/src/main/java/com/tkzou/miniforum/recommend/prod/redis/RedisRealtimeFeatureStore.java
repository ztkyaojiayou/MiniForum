package com.tkzou.miniforum.recommend.prod.redis;

import com.tkzou.miniforum.recommend.feature.RealtimeFeature;
import com.tkzou.miniforum.recommend.feature.RealtimeFeatureStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.Optional;

/**
 * Redis 实时特征存储（生产适配，@Profile("prod") 激活，默认不加载）
 * <p>
 * <b>数据流程</b>：近线层（Flink 窗口）{@link #put} → Redis String 存 JSON（key = "realtime:{key}"，TTL=60s）
 * → 在线层 {@link #get} 读取实时特征。生产更细的形态可用 Hash + HEXPIRE 逐字段过期，此处演示核心机制。
 * <p>
 * 连接用 {@link JedisPool}（在线请求并发读，跨线程安全），{@link #close()} 由 Spring {@link PreDestroy} 触发。
 */
@Component
@Profile("prod")
public class RedisRealtimeFeatureStore implements RealtimeFeatureStore {

    private static final Logger log = LoggerFactory.getLogger(RedisRealtimeFeatureStore.class);
    private static final String KEY_PREFIX = "realtime:";
    private static final int TTL_SECONDS = 60;

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public RedisRealtimeFeatureStore(
            @Value("${app.rec.redis.host:localhost}") String host,
            @Value("${app.rec.redis.port:6379}") int port,
            ObjectMapper objectMapper) {
        this.jedisPool = new JedisPool(host, port);
        this.objectMapper = objectMapper;
        log.info("Redis 实时特征存储初始化完成，{}:{}", host, port);
    }

    @Override
    public void put(String key, RealtimeFeature feature) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(KEY_PREFIX + key, objectMapper.writeValueAsString(feature));
            jedis.expire(KEY_PREFIX + key, TTL_SECONDS);
        } catch (Exception e) {
            log.warn("实时特征写入 Redis 失败：{}", e.getMessage());
        }
    }

    @Override
    public Optional<RealtimeFeature> get(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(KEY_PREFIX + key);
            if (json == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(objectMapper.readValue(json, RealtimeFeature.class));
            } catch (Exception e) {
                log.warn("实时特征读取失败：{}", e.getMessage());
                return Optional.empty();
            }
        }
    }

    /** 批量读取（生产用 pipeline HMGET 一次取数百实体特征） */
    public Map<String, RealtimeFeature> mget(java.util.Set<String> keys) {
        java.util.Map<String, RealtimeFeature> result = new java.util.HashMap<>();
        for (String key : keys) {
            get(key).ifPresent(f -> result.put(key, f));
        }
        return result;
    }

    @PreDestroy
    public void close() {
        jedisPool.close();
        log.info("Redis 实时特征存储连接池已关闭");
    }
}
