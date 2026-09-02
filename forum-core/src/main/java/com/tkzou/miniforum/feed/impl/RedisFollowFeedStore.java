package com.tkzou.miniforum.feed.impl;

import com.tkzou.miniforum.feed.FollowFeedStore;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    /** 大V分流阈值：粉丝数 ≥ 此值时 isBigV 返回 true（走拉） */
    private final int bigVThreshold;

    /** 大V集合 Redis SET key（member = 作者 id） */
    private static final String BIGV_KEY = "feed:bigvs";

    /** 大V集合是否已从存量关注关系初始化（懒扫描一次，见 {@link #ensureBigVsScanned}） */
    private volatile boolean bigVsScanned = false;

    public RedisFollowFeedStore(FollowRepository followRepository,
                                @Value("${app.rec.redis.host:localhost}") String host,
                                @Value("${app.rec.redis.port:6379}") int port,
                                @Value("${app.rec.feed.cap:500}") int cap,
                                @Value("${app.rec.feed.big-v-fan-threshold:100000}") int bigVThreshold) {
        this.followRepository = followRepository;
        this.jedisPool = new JedisPool(host, port);
        this.cap = cap;
        this.bigVThreshold = bigVThreshold;
        log.info("Redis 关注流 inbox 初始化完成，{}:{}（inbox 封顶 {} 条，大V分流阈值 {}）", host, port, cap, bigVThreshold);
    }

    @Override
    public void fanout(Long authorId, Long postId) {
        // 大V分流：粉丝超阈值跳过扇出（走拉，新帖只写自己的 outbox，粉丝读时拉取合并）
        if (isBigV(authorId)) {
            log.warn("跳过扇出：作者 {} 粉丝数超阈值（走拉，新帖进自己的 outbox）", authorId);
            return;
        }
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
    public List<Long> getInbox(Long userId, Long maxId, int maxCount) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (!jedis.exists(builtKey(userId))) {
                return List.of();
            }
            String key = inboxKey(userId);
            Set<String> members = maxId == null
                    ? jedis.zrevrange(key, 0, maxCount - 1)                           // 最新（最大 score）在前
                    : jedis.zrevrangeByScore(key, "(" + maxId, "-inf", 0, maxCount);  // 上限开区间，下一页不重复
            List<Long> result = new ArrayList<>();
            for (String m : members) {
                result.add(Long.parseLong(m));
            }
            return result;
        }
    }

    @Override
    public List<Long> getInboxAfter(Long userId, Long sinceId, int maxCount) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (!jedis.exists(builtKey(userId)) || sinceId == null) {
                return List.of();
            }
            // 下限开区间（严格 > sinceId），最新在前，用于增量刷新
            Set<String> members = jedis.zrevrangeByScore(inboxKey(userId), "+inf", "(" + sinceId, 0, maxCount);
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

    @Override
    public boolean isBigV(Long authorId) {
        ensureBigVsScanned();
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember(BIGV_KEY, String.valueOf(authorId));
        }
    }

    @Override
    public Set<Long> bigVIds() {
        ensureBigVsScanned();
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.smembers(BIGV_KEY).stream()
                    .map(Long::valueOf)
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public void refreshBigV(Long authorId) {
        // 事件驱动重数粉丝数：跨过阈值 SADD，掉出阈值 SREM（粉丝数只在关系边变化时改变）
        boolean big = followRepository.countByFolloweeId(authorId) >= bigVThreshold;
        try (Jedis jedis = jedisPool.getResource()) {
            if (big) {
                jedis.sadd(BIGV_KEY, String.valueOf(authorId));
            } else {
                jedis.srem(BIGV_KEY, String.valueOf(authorId));
            }
        }
    }

    @Override
    public List<Long> getAuthorTimeline(Long authorId, Long maxId, int maxCount) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = outboxKey(authorId);
            Set<String> members = maxId == null
                    ? jedis.zrevrange(key, 0, maxCount - 1)                           // 最新（最大 score）在前
                    : jedis.zrevrangeByScore(key, "(" + maxId, "-inf", 0, maxCount);  // 上限开区间，下一页不重复
            List<Long> result = new ArrayList<>();
            for (String m : members) {
                result.add(Long.parseLong(m));
            }
            return result;
        }
    }

    @Override
    public void writeOutbox(Long authorId, Long postId) {
        // 大V发帖：只写自己的 outbox（O(1)），不扇出；读者读时拉取合并
        try (Jedis jedis = jedisPool.getResource()) {
            String key = outboxKey(authorId);
            jedis.zadd(key, postId, String.valueOf(postId));
            jedis.zremrangeByRank(key, 0, -cap - 1); // 封顶：与 inbox 一致
        }
    }

    /** 懒扫描：首次判定大V时用存量关注关系（MySQL 事实）初始化全局集合（一次性全表，演示量级可接受） */
    private void ensureBigVsScanned() {
        if (bigVsScanned) {
            return;
        }
        synchronized (this) {
            if (bigVsScanned) {
                return;
            }
            Map<Long, Long> counts = new HashMap<>();
            for (Follow f : followRepository.exportAll()) {
                counts.merge(f.getFolloweeId(), 1L, Long::sum);
            }
            try (Jedis jedis = jedisPool.getResource()) {
                Pipeline pipe = jedis.pipelined();
                counts.forEach((authorId, cnt) -> {
                    if (cnt >= bigVThreshold) {
                        pipe.sadd(BIGV_KEY, String.valueOf(authorId));
                    }
                });
                pipe.sync();
            }
            bigVsScanned = true;
        }
    }

    private String outboxKey(Long authorId) {
        return "feed:outbox:" + authorId;
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
