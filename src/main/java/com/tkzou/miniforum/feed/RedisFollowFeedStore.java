package com.tkzou.miniforum.feed;

import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.repository.FollowRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Redis 关注流 inbox（生产适配，@Profile("prod") 激活）
 * <p>
 * 生产级关注流的标准存储：Redis ZSet `feed:inbox:{uid}`，member=postId，score=postId（单调递增即时间序）；
 * 建流状态用独立标记 `feed:built:{uid}`（ZSet 无法建空 key，空流用户也需要标记"已建"）。
 * <ul>
 *   <li><b>扇出</b>：发帖时 pipeline 批量 ZADD 到已建流粉丝的 inbox，并 ZREMRANGEBYRANK 封顶；</li>
 *   <li><b>读取</b>：ZREVRANGE（最新在前）取 postId，内容按 id 批量回源；</li>
 *   <li><b>回填</b>：关注新作者 / 首次建流时把其近期 postId ZADD 进 inbox，并 SET 建流标记。</li>
 * </ul>
 * 连接使用 {@link JedisPool}（fanout 在 Kafka 消费者线程、读取在 Web 请求线程，需跨线程安全），
 * {@link #close()} 由 Spring {@link PreDestroy} 触发。
 * 生产扇出由 Kafka 消费者（prod.kafka.KafkaPostCreatedConsumer）异步触发，避免发帖请求被拖慢。
 * 启用：-Pprod 构建 + spring.profiles.active=prod + 配置 app.rec.redis.host/port。
 */
@Component
@Profile("prod")
public class RedisFollowFeedStore implements FollowFeedStore {

    private static final Logger log = LoggerFactory.getLogger(RedisFollowFeedStore.class);

    private final FollowRepository followRepository;
    private final JedisPool jedisPool;
    private final int cap;

    public RedisFollowFeedStore(FollowRepository followRepository,
                                @Value("${app.rec.redis.host:localhost}") String host,
                                @Value("${app.rec.redis.port:6379}") int port,
                                @Value("${app.rec.feed.cap:500}") int cap) {
        this.followRepository = followRepository;
        this.jedisPool = new JedisPool(host, port);
        this.cap = cap;
        log.info("Redis 关注流 inbox 初始化完成，{}:{}（inbox 封顶 {} 条）", host, port, cap);
    }

    @Override
    public void fanout(Long authorId, Long postId) {
        List<Follow> followers = followRepository.findByFolloweeId(authorId);
        if (followers.isEmpty()) {
            return;
        }
        try (Jedis jedis = jedisPool.getResource()) {
            // 只写给已建流的粉丝：未建流用户首次读取会用完整关注集合回填，避免建成"半成品流"
            List<String> keys = new ArrayList<>();
            for (Follow f : followers) {
                if (jedis.exists(builtKey(f.getFollowerId()))) {
                    keys.add(inboxKey(f.getFollowerId()));
                }
            }
            if (keys.isEmpty()) {
                return;
            }
            // pipeline 批量写每个已建流粉丝的 inbox + 封顶
            try (Pipeline pipe = jedis.pipelined()) {
                for (String key : keys) {
                    pipe.zadd(key, postId, String.valueOf(postId));
                    pipe.zremrangeByRank(key, 0, -cap - 1);
                }
                pipe.sync();
            }
        }
    }

    @Override
    public List<Long> getInbox(Long userId, int maxCount) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (!jedis.exists(builtKey(userId))) {
                return List.of();
            }
            Set<String> members = jedis.zrevrange(inboxKey(userId), 0, maxCount - 1); // 最新（最大 score）在前
            List<Long> result = new ArrayList<>();
            for (String m : members) {
                result.add(Long.parseLong(m));
            }
            return result;
        }
    }

    @Override
    public void onFollow(Long followerId, List<Long> recentPostIds) {
        try (Jedis jedis = jedisPool.getResource()) {
            // 无论是否有历史帖都先标记已建流（空流用户也避免每次读取回退全表）
            jedis.set(builtKey(followerId), "1");
            if (recentPostIds == null || recentPostIds.isEmpty()) {
                return;
            }
            String key = inboxKey(followerId);
            try (Pipeline pipe = jedis.pipelined()) {
                for (Long postId : recentPostIds) {
                    pipe.zadd(key, postId, String.valueOf(postId));
                }
                pipe.zremrangeByRank(key, 0, -cap - 1);
                pipe.sync();
            }
        }
    }

    @Override
    public boolean isBuilt(Long userId) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(builtKey(userId));
        }
    }

    @PreDestroy
    public void close() {
        jedisPool.close();
        log.info("Redis 关注流 inbox 连接池已关闭");
    }

    private String inboxKey(Long userId) {
        return "feed:inbox:" + userId;
    }

    private String builtKey(Long userId) {
        return "feed:built:" + userId;
    }
}
