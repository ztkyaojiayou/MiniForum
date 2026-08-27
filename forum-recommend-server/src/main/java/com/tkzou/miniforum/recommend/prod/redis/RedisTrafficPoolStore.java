package com.tkzou.miniforum.recommend.prod.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.coldstart.PostState;
import com.tkzou.miniforum.recommend.coldstart.TrafficPoolStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;
import redis.clients.jedis.params.SetParams;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Redis 流量池状态存储（生产适配，@Profile("prod") 激活，默认不加载）
 * <p>
 * key "traffic:{postId}" = PostState JSON，TTL 默认 7 天（停止后自然过期，替代进程内 cleanup）。
 * <b>putIfAbsent 用 SET NX EX 原子入池</b>（多 pod 去重，对应 notifyCreated）。
 * <p>
 * 局限（已文档化）：get/put 为"读-改-写"，跨 pod 并发 exposures++ 可能丢计数、晋级判定可能双触发。
 * 生产升级路径：①计数改 Redis Hash + HINCRBY 原子自增；②晋级判定（读全字段→决策→写回）用 Lua EVAL 原子化。
 * 连接用 {@link JedisPool}，{@link #close()} 由 Spring {@link PreDestroy} 触发。
 */
@Component
@Profile("prod")
public class RedisTrafficPoolStore implements TrafficPoolStore {

    private static final Logger log = LoggerFactory.getLogger(RedisTrafficPoolStore.class);
    private static final String KEY_PREFIX = "traffic:";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;
    private final long defaultTtlSeconds;

    public RedisTrafficPoolStore(
            @Value("${app.rec.redis.host:localhost}") String host,
            @Value("${app.rec.redis.port:6379}") int port,
            @Value("${app.rec.traffic-pool.redis-ttl-seconds:604800}") long defaultTtlSeconds,
            ObjectMapper objectMapper) {
        this(new JedisPool(host, port), defaultTtlSeconds, objectMapper);
    }

    /** 包内可见（测试注入 mock 连接池用）；生产走 @Value 构造 */
    RedisTrafficPoolStore(JedisPool jedisPool, long defaultTtlSeconds, ObjectMapper objectMapper) {
        this.jedisPool = jedisPool;
        this.defaultTtlSeconds = defaultTtlSeconds;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<PostState> get(Long postId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(KEY_PREFIX + postId);
            if (json == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(objectMapper.readValue(json, PostState.class));
            } catch (Exception e) {
                log.warn("流量池状态读取失败：{}", e.getMessage());
                return Optional.empty();
            }
        }
    }

    @Override
    public void put(Long postId, PostState state) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(KEY_PREFIX + postId, objectMapper.writeValueAsString(state));
            jedis.expire(KEY_PREFIX + postId, defaultTtlSeconds);
        } catch (Exception e) {
            log.warn("流量池状态写入 Redis 失败：{}", e.getMessage());
        }
    }

    @Override
    public boolean putIfAbsent(Long postId, PostState state, long ttlSeconds) {
        try (Jedis jedis = jedisPool.getResource()) {
            String ok = jedis.set(KEY_PREFIX + postId, objectMapper.writeValueAsString(state),
                    new SetParams().nx().ex(ttlSeconds));
            return "OK".equals(ok);
        } catch (Exception e) {
            log.warn("流量池原子入池失败：{}", e.getMessage());
            return false;
        }
    }

    @Override
    public void remove(Long postId) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(KEY_PREFIX + postId);
        }
    }

    @Override
    public Map<Long, PostState> all() {
        Map<Long, PostState> result = new HashMap<>();
        try (Jedis jedis = jedisPool.getResource()) {
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(KEY_PREFIX + "*").count(100);
            do {
                ScanResult<String> scan = jedis.scan(cursor, params);
                for (String key : scan.getResult()) {
                    Long postId = Long.valueOf(key.substring(KEY_PREFIX.length()));
                    get(postId).ifPresent(st -> result.put(postId, st));
                }
                cursor = scan.getCursor();
            } while (!"0".equals(cursor));
        } catch (Exception e) {
            log.warn("流量池全量扫描失败：{}", e.getMessage());
        }
        return result;
    }

    @Override
    public int size() {
        return all().size();
    }

    @PreDestroy
    public void close() {
        jedisPool.close();
        log.info("Redis 流量池状态存储连接池已关闭");
    }
}
