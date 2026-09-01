package com.tkzou.miniforum.idempotency.impl;

import com.tkzou.miniforum.idempotency.IdempotencyStore;
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
 * Redis 幂等存储（生产适配，@Profile("prod")）
 * <p>
 * key `idempotency:{key}`，值 "PROCESSING"（占位中）或 postId 字符串。
 * <ul>
 *   <li>acquire：SET key PROCESSING NX EX ttl（原子占位，拿到返回 true）；</li>
 *   <li>complete：SETEX key ttl postId；getCompleted：GET key（非 PROCESSING 解析 postId）；release：DEL key。</li>
 * </ul>
 * TTL 默认 5 分钟（app.idempotency.ttl-ms），过期后同一 key 可重新提交。
 */
@Component
@Profile("prod")
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyStore.class);
    private static final String PROCESSING = "PROCESSING";

    private final JedisPool jedisPool;
    private final long ttlSec;

    public RedisIdempotencyStore(@Value("${app.rec.redis.host:localhost}") String host,
                                 @Value("${app.rec.redis.port:6379}") int port,
                                 @Value("${app.idempotency.ttl-ms:300000}") long ttlMs) {
        this.jedisPool = new JedisPool(host, port);
        this.ttlSec = Math.max(1, ttlMs / 1000);
        log.info("Redis 幂等存储初始化完成，{}:{}（TTL {}s）", host, port, ttlSec);
    }

    @Override
    public Optional<Long> getCompleted(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(idempotencyKey(key));
            if (value == null || PROCESSING.equals(value)) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(value));
        }
    }

    @Override
    public boolean acquire(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            // SET key PROCESSING NX EX ttl：原子占位，拿到返回 OK
            String result = jedis.set(idempotencyKey(key), PROCESSING,
                    new SetParams().nx().ex(ttlSec));
            return "OK".equals(result);
        }
    }

    @Override
    public void complete(String key, Long postId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(idempotencyKey(key), ttlSec, String.valueOf(postId));
        }
    }

    @Override
    public void release(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(idempotencyKey(key));
        }
    }

    @PreDestroy
    public void close() {
        jedisPool.close();
        log.info("Redis 幂等存储连接池已关闭");
    }

    private String idempotencyKey(String key) {
        return "idempotency:" + key;
    }
}
