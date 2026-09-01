package com.tkzou.miniforum.recommend.prod.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.coldstart.AlphaBeta;
import com.tkzou.miniforum.recommend.coldstart.NewItemPoolStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import javax.annotation.PreDestroy;
import java.util.Optional;

/**
 * Redis Thompson 后验存储（生产适配，@Profile("prod") 激活，默认不加载）
 * <p>
 * key "coldstart:{itemId}" = AlphaBeta JSON，TTL 默认 30 天。
 * 局限：recordOutcome 的"读-改-写"跨 pod 并发可能偏置（pendingExposures 达阈值判定），
 * 生产升级路径：用 Redis Hash + HINCRBY（alpha/beta/pending 三字段原子自增）或 Lua 脚本。
 */
@Component
@Profile("prod")
public class RedisNewItemPoolStore implements NewItemPoolStore {

    private static final Logger log = LoggerFactory.getLogger(RedisNewItemPoolStore.class);
    private static final String KEY_PREFIX = "coldstart:";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final long defaultTtlSeconds;

    public RedisNewItemPoolStore(
            @Value("${app.rec.redis.host:localhost}") String host,
            @Value("${app.rec.redis.port:6379}") int port,
            @Value("${app.rec.coldstart.redis-ttl-seconds:2592000}") long defaultTtlSeconds,
            ObjectMapper objectMapper) {
        this(new JedisPool(host, port), defaultTtlSeconds, objectMapper);
    }

    /** 包内可见（测试注入 mock 连接池用）；生产走 @Value 构造 */
    RedisNewItemPoolStore(JedisPool jedisPool, long defaultTtlSeconds, ObjectMapper objectMapper) {
        this.jedisPool = jedisPool;
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AlphaBeta> get(Long itemId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(KEY_PREFIX + itemId);
            if (json == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(objectMapper.readValue(json, AlphaBeta.class));
            } catch (Exception e) {
                log.warn("Thompson 后验读取失败", e);
                return Optional.empty();
            }
        }
    }

    @Override
    public void put(Long itemId, AlphaBeta ab) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(KEY_PREFIX + itemId, objectMapper.writeValueAsString(ab));
            jedis.expire(KEY_PREFIX + itemId, defaultTtlSeconds);
        } catch (Exception e) {
            log.warn("Thompson 后验写入 Redis 失败", e);
        }
    }

    @Override
    public boolean putIfAbsent(Long itemId, AlphaBeta ab, long ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String ok = jedis.set(KEY_PREFIX + itemId, objectMapper.writeValueAsString(ab),
                    new SetParams().nx().ex(ttlSeconds));
            return "OK".equals(ok);
        } catch (Exception e) {
            log.warn("Thompson 后验原子创建失败", e);
            return false;
        }
    }

    @Override
    public void remove(Long itemId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(KEY_PREFIX + itemId);
        }
    }

    @Override
    public boolean containsKey(Long itemId) {
        try (Jedis jedis = jedisPool.getResource()) {
            return Boolean.TRUE.equals(jedis.exists(KEY_PREFIX + itemId));
        }
    }

    @PreDestroy
    public void close() {
        jedisPool.close();
        log.info("Redis Thompson 后验存储连接池已关闭");
    }
}
