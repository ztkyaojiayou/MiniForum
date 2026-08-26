package com.tkzou.miniforum.recommend.prod.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.feature.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PreDestroy;
import java.util.Optional;

/**
 * Redis 用户画像存储（生产适配，@Profile("prod")）
 * <p>
 * <b>数据流程</b>：在线推荐读画像时，未命中则现算（UserProfileAggregator）并写回 Redis
 * {@code profile:{uid}}（TTL 1h）；命中则直接读——<b>跨实例共享同一份画像</b>，避免每实例各自现算，
 * 对齐主流"画像落 Redis"（见 docs/数据存储矩阵.md）。
 */
@Component
@Profile("prod")
public class RedisUserProfileStore {

    private static final Logger log = LoggerFactory.getLogger(RedisUserProfileStore.class);
    private static final String KEY_PREFIX = "profile:";
    /** 画像 TTL：行为回流会持续更新画像，容忍缓存延迟（1 小时） */
    private static final int TTL_SECONDS = 3600;

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public RedisUserProfileStore(
            @Value("${app.rec.redis.host:localhost}") String host,
            @Value("${app.rec.redis.port:6379}") int port,
            ObjectMapper objectMapper) {
        this.jedisPool = new JedisPool(host, port);
        this.objectMapper = objectMapper;
        log.info("Redis 用户画像存储初始化完成，{}:{}", host, port);
    }

    public void put(Long userId, UserProfile profile) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.setex(KEY_PREFIX + userId, TTL_SECONDS, objectMapper.writeValueAsString(profile));
        } catch (Exception e) {
            log.warn("用户画像写入 Redis 失败：{}", e.getMessage());
        }
    }

    public Optional<UserProfile> get(Long userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(KEY_PREFIX + userId);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, UserProfile.class));
        } catch (Exception e) {
            log.warn("用户画像读取失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    @PreDestroy
    public void close() {
        jedisPool.close();
        log.info("Redis 用户画像存储连接池已关闭");
    }
}
