package com.tkzou.miniforum.recommend.prod.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * ItemCF 模型 Redis 存取（生产适配，@Profile("prod") 激活，默认不加载）
 * <p>
 * 数据流程（P2-3 离线发布 → 在线读取）：离线（offline-job 的 ItemCfModelPublisher）构建模型 →
 * {@link #publish} 序列化写 Redis key "itemcf:latest" → 在线 {@link ItemCfModelStore#get} 经 {@link #get}
 * 读回（多实例共享同一模型，免每实例本地重建）。
 * 序列化用 simMap（SimilarItem 是 record，Jackson 2.13 原生支持）。
 */
@Component
@Profile("prod")
public class ItemCfModelRedisStore {

    private static final Logger log = LoggerFactory.getLogger(ItemCfModelRedisStore.class);
    private static final String KEY = "itemcf:latest";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper;

    public ItemCfModelRedisStore(
            @Value("${app.rec.redis.host:localhost}") String host,
            @Value("${app.rec.redis.port:6379}") int port,
            ObjectMapper objectMapper) {
        this(new JedisPool(host, port), objectMapper);
    }

    /** 包内可见（测试注入 mock 连接池用）；生产走 @Value 构造 */
    ItemCfModelRedisStore(JedisPool jedisPool, ObjectMapper objectMapper) {
        this.jedisPool = jedisPool;
        this.objectMapper = objectMapper;
    }

    /** 读取已发布模型（无/解析失败 → empty，在线回退本地重建） */
    public Optional<ItemCfModel> get() {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = jedis.get(KEY);
            if (json == null) {
                return Optional.empty();
            }
            Map<Long, List<ItemCfModel.SimilarItem>> map = objectMapper.readValue(
                    json, new TypeReference<Map<Long, List<ItemCfModel.SimilarItem>>>() {});
            return Optional.of(ItemCfModel.from(map));
        } catch (Exception e) {
            log.warn("ItemCF 模型读取失败：{}", e.getMessage());
            return Optional.empty();
        }
    }

    /** 发布模型（离线构建后写 Redis，供在线多实例读取） */
    public void publish(ItemCfModel model) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(KEY, objectMapper.writeValueAsString(model.getSimMap()));
        } catch (Exception e) {
            log.warn("ItemCF 模型发布失败：{}", e.getMessage());
        }
    }

    @PreDestroy
    public void close() {
        jedisPool.close();
        log.info("ItemCF 模型 Redis 存取连接池已关闭");
    }
}
