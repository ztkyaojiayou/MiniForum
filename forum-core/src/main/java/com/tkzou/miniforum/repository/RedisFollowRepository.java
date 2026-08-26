package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Follow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Redis 关注关系仓库（生产适配，@Profile("prod") 激活）
 * <p>
 * 生产级关注关系用 Redis 承载高频读写（isFollowing 判断、关注/粉丝列表、关注流 fanout）。
 * 存储结构：
 * <ul>
 *   <li><code>follow:rel:{id}</code> Hash：{followerId, followeeId, createdAt}（关系事实）</li>
 *   <li><code>follow:index:{followerId}:{followeeId}</code> → id（快速判断/定位）</li>
 *   <li><code>follow:following:{fid}</code> ZSET（followeeId → 时间，最新在前）</li>
 *   <li><code>follow:followers:{eid}</code> ZSET（followerId → 时间，最新在前）</li>
 * </ul>
 * 持久化（exportAll/importAll）走 Redis 本身，MySQL mini_store 仍会快照兜底。
 * 启用：-Pprod 构建 + spring.profiles.active=prod + 配置 app.rec.redis.host/port。
 */
@Repository
@Profile("prod")
public class RedisFollowRepository implements FollowRepository {
    /** ID 生成器：Spring 注入（演示=实体生成器 / 生产=Snowflake），测试无 Spring 时用默认实体生成器 */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();


    private static final Logger log = LoggerFactory.getLogger(RedisFollowRepository.class);

    private final Jedis jedis;

    public RedisFollowRepository(
            @Value("${app.rec.redis.host:localhost}") String host,
            @Value("${app.rec.redis.port:6379}") int port) {
        this.jedis = new Jedis(host, port);
        log.info("Redis 关注关系仓库初始化完成，{}:{}", host, port);
    }

    @Override
    public Follow save(Follow follow) {
        if (follow.getId() == null) {
            follow.setId(idProvider.next("Follow"));
        }
        Map<String, String> h = new HashMap<>();
        h.put("followerId", String.valueOf(follow.getFollowerId()));
        h.put("followeeId", String.valueOf(follow.getFolloweeId()));
        h.put("createdAt", follow.getCreatedAt() == null ? null : follow.getCreatedAt().toString());
        jedis.hset(relKey(follow.getId()), h);
        jedis.set(indexKey(follow.getFollowerId(), follow.getFolloweeId()), String.valueOf(follow.getId()));
        long score = follow.getCreatedAt() == null ? System.currentTimeMillis()
                : follow.getCreatedAt().toEpochSecond(java.time.ZoneOffset.UTC) * 1000L;
        jedis.zadd(followingKey(follow.getFollowerId()), score, String.valueOf(follow.getFolloweeId()));
        jedis.zadd(followersKey(follow.getFolloweeId()), score, String.valueOf(follow.getFollowerId()));
        return follow;
    }

    @Override
    public Optional<Follow> findByFollowerAndFollowee(Long followerId, Long followeeId) {
        String id = jedis.get(indexKey(followerId, followeeId));
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(fromHash(jedis.hgetAll(relKey(Long.parseLong(id))), Long.parseLong(id)));
    }

    @Override
    public boolean exists(Long followerId, Long followeeId) {
        return jedis.exists(indexKey(followerId, followeeId));
    }

    @Override
    public void delete(Follow follow) {
        jedis.del(relKey(follow.getId()));
        jedis.del(indexKey(follow.getFollowerId(), follow.getFolloweeId()));
        jedis.zrem(followingKey(follow.getFollowerId()), String.valueOf(follow.getFolloweeId()));
        jedis.zrem(followersKey(follow.getFolloweeId()), String.valueOf(follow.getFollowerId()));
    }

    @Override
    public List<Follow> findByFollowerId(Long followerId) {
        return buildFollows(followingKey(followerId), followerId, true);
    }

    @Override
    public List<Follow> findByFolloweeId(Long followeeId) {
        return buildFollows(followersKey(followeeId), followeeId, false);
    }

    /** 从 ZSET（最新在前）重建 Follow 列表 */
    private List<Follow> buildFollows(String zsetKey, Long selfId, boolean isFollowing) {
        Set<String> members = jedis.zrevrange(zsetKey, 0, -1);
        List<Follow> result = new ArrayList<>();
        for (String other : members) {
            long otherId = Long.parseLong(other);
            String id = jedis.get(isFollowing ? indexKey(selfId, otherId) : indexKey(otherId, selfId));
            if (id != null) {
                result.add(fromHash(jedis.hgetAll(relKey(Long.parseLong(id))), Long.parseLong(id)));
            }
        }
        return result;
    }

    @Override
    public long countByFollowerId(Long followerId) {
        return jedis.zcard(followingKey(followerId));
    }

    @Override
    public long countByFolloweeId(Long followeeId) {
        return jedis.zcard(followersKey(followeeId));
    }

    @Override
    public void deleteByUserId(Long userId) {
        // 删除"该用户关注的人"方向
        Set<String> following = jedis.zrange(followingKey(userId), 0, -1);
        for (String eid : following) {
            long followeeId = Long.parseLong(eid);
            String id = jedis.get(indexKey(userId, followeeId));
            if (id != null) {
                jedis.del(relKey(Long.parseLong(id)));
            }
            jedis.del(indexKey(userId, followeeId));
            jedis.zrem(followersKey(followeeId), String.valueOf(userId));
        }
        // 删除"粉丝关注该用户"方向
        Set<String> followers = jedis.zrange(followersKey(userId), 0, -1);
        for (String fid : followers) {
            long followerId = Long.parseLong(fid);
            String id = jedis.get(indexKey(followerId, userId));
            if (id != null) {
                jedis.del(relKey(Long.parseLong(id)));
            }
            jedis.del(indexKey(followerId, userId));
            jedis.zrem(followingKey(followerId), String.valueOf(userId));
        }
        jedis.del(followingKey(userId));
        jedis.del(followersKey(userId));
    }

    @Override
    public List<Follow> exportAll() {
        List<Follow> result = new ArrayList<>();
        String cursor = "0";
        do {
            ScanResult<String> scan = jedis.scan(cursor, new ScanParams().match("follow:rel:*").count(200));
            cursor = scan.getCursor();
            for (String key : scan.getResult()) {
                long id = Long.parseLong(key.substring("follow:rel:".length()));
                result.add(fromHash(jedis.hgetAll(key), id));
            }
        } while (!"0".equals(cursor));
        result.sort(Comparator.comparingLong(Follow::getId));
        return result;
    }

    @Override
    public void importAll(List<Follow> follows) {
        // 清空全部 follow:* 键后重建
        String cursor = "0";
        do {
            ScanResult<String> scan = jedis.scan(cursor, new ScanParams().match("follow:*").count(200));
            cursor = scan.getCursor();
            for (String key : scan.getResult()) {
                jedis.del(key);
            }
        } while (!"0".equals(cursor));
        if (follows != null) {
            for (Follow f : follows) {
                save(f);
            }
        }
    }

    private Follow fromHash(Map<String, String> h, Long id) {
        Follow f = new Follow();
        f.setId(id);
        f.setFollowerId(Long.parseLong(h.get("followerId")));
        f.setFolloweeId(Long.parseLong(h.get("followeeId")));
        if (h.get("createdAt") != null) {
            f.setCreatedAt(LocalDateTime.parse(h.get("createdAt")));
        }
        return f;
    }

    private String relKey(Long id) {
        return "follow:rel:" + id;
    }

    private String indexKey(Long followerId, Long followeeId) {
        return "follow:index:" + followerId + ":" + followeeId;
    }

    private String followingKey(Long followerId) {
        return "follow:following:" + followerId;
    }

    private String followersKey(Long followeeId) {
        return "follow:followers:" + followeeId;
    }
}
