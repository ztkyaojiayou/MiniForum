package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 事实 + Redis 缓存的关注关系仓库（生产适配，@Profile("prod")）
 * <p>
 * <b>主流形态</b>（docs/数据存储矩阵.md）：<b>user_follow 表 = 关注关系事实源</b>（唯一不可丢，
 * 所有读以它为准）；<b>Redis following/followers ZSET = 热缓存</b>（写时维护，计数走 ZCARD 扛高频读，
 * 如 fanout 大V阈值、作者热度）。
 * <ul>
 *   <li>写（save/delete）：MySQL 事实 + Redis 双向 ZADD/ZREM 同步；</li>
 *   <li>列表读（findByFollowerId/findByFolloweeId）：MySQL 索引查询（准确、可拼 createdAt）；</li>
 *   <li>计数（countBy*）：Redis ZCARD（key 存在时），未建则回退 MySQL COUNT。</li>
 * </ul>
 * 启用：-Pprod 构建 + spring.profiles.active=prod + spring.datasource.* + app.rec.redis.host/port。
 */
@Repository
@Profile("prod")
public class MySqlFollowRepository implements FollowRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlFollowRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final JedisPool jedisPool;
    /** ID 生成器（生产 = Snowflake） */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    public MySqlFollowRepository(JdbcTemplate jdbcTemplate,
                                 @Value("${app.rec.redis.host:localhost}") String host,
                                 @Value("${app.rec.redis.port:6379}") int port) {
        this.jdbcTemplate = jdbcTemplate;
        this.jedisPool = new JedisPool(host, port);
        log.info("MySQL 关注关系仓库初始化（user_follow 事实 + Redis 缓存），{}:{}", host, port);
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS user_follow ("
                + "id BIGINT PRIMARY KEY,"
                + "follower_id BIGINT NOT NULL,"
                + "followee_id BIGINT NOT NULL,"
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                + "UNIQUE KEY uk_follow (follower_id, followee_id),"
                + "KEY idx_followee (followee_id, created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        // 存量表补索引（CREATE TABLE IF NOT EXISTS 不作用于已存在表）：按信息模式判存在，幂等
        ensureIndex("user_follow", "idx_followee", "ALTER TABLE user_follow ADD INDEX idx_followee (followee_id, created_at)");
    }

    /** 幂等补索引：information_schema 判定不存在时才执行 ALTER（避免 CREATE TABLE IF NOT EXISTS 不作用于存量表） */
    private void ensureIndex(String table, String index, String alterSql) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.statistics "
                        + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class, table, index);
        if (n == null || n == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }

    @Override
    public Follow save(Follow follow) {
        if (follow.getId() == null) {
            follow.setId(idProvider.next("Follow"));
        }
        if (follow.getCreatedAt() == null) {
            follow.setCreatedAt(LocalDateTime.now());
        }
        // 事实：MySQL upsert（follower_id+followee_id 唯一）
        jdbcTemplate.update(
                "INSERT INTO user_follow(id, follower_id, followee_id, created_at) VALUES(?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE created_at = VALUES(created_at)",
                follow.getId(), follow.getFollowerId(), follow.getFolloweeId(), follow.getCreatedAt());
        // 缓存：Redis 双向 ZADD（following:{fid} / followers:{eid}）
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipe = jedis.pipelined();
            pipe.zadd(followingKey(follow.getFollowerId()), System.currentTimeMillis(), String.valueOf(follow.getFolloweeId()));
            pipe.zadd(followersKey(follow.getFolloweeId()), System.currentTimeMillis(), String.valueOf(follow.getFollowerId()));
            pipe.sync();
        }
        return follow;
    }

    @Override
    public Optional<Follow> findByFollowerAndFollowee(Long followerId, Long followeeId) {
        return jdbcTemplate.query(
                "SELECT id, follower_id, followee_id, created_at FROM user_follow WHERE follower_id=? AND followee_id=?",
                this::mapFollow, followerId, followeeId).stream().findFirst();
    }

    @Override
    public boolean exists(Long followerId, Long followeeId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE follower_id=? AND followee_id=?",
                Integer.class, followerId, followeeId);
        return n != null && n > 0;
    }

    @Override
    public void delete(Follow follow) {
        jdbcTemplate.update("DELETE FROM user_follow WHERE id=?", follow.getId());
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipe = jedis.pipelined();
            pipe.zrem(followingKey(follow.getFollowerId()), String.valueOf(follow.getFolloweeId()));
            pipe.zrem(followersKey(follow.getFolloweeId()), String.valueOf(follow.getFollowerId()));
            pipe.sync();
        }
    }

    @Override
    public List<Follow> findByFollowerId(Long followerId) {
        return jdbcTemplate.query(
                "SELECT id, follower_id, followee_id, created_at FROM user_follow WHERE follower_id=? ORDER BY created_at DESC, id DESC",
                this::mapFollow, followerId);
    }

    @Override
    public List<Follow> findByFolloweeId(Long followeeId) {
        return jdbcTemplate.query(
                "SELECT id, follower_id, followee_id, created_at FROM user_follow WHERE followee_id=? ORDER BY created_at DESC, id DESC",
                this::mapFollow, followeeId);
    }

    @Override
    public long countByFollowerId(Long followerId) {
        Long cached = cachedCount(followingKey(followerId));
        if (cached != null) {
            return cached;
        }
        return countByColumn(followerId, "follower_id");
    }

    @Override
    public long countByFolloweeId(Long followeeId) {
        Long cached = cachedCount(followersKey(followeeId));
        if (cached != null) {
            return cached;
        }
        return countByColumn(followeeId, "followee_id");
    }

    @Override
    public void deleteByUserId(Long userId) {
        // 先取该用户所有关系（用于清理 Redis 缓存），再 MySQL 删除
        List<Follow> asFollower = findByFollowerId(userId);
        List<Follow> asFollowee = findByFolloweeId(userId);
        jdbcTemplate.update("DELETE FROM user_follow WHERE follower_id=? OR followee_id=?", userId, userId);
        try (Jedis jedis = jedisPool.getResource()) {
            Pipeline pipe = jedis.pipelined();
            pipe.del(followingKey(userId), followersKey(userId));
            for (Follow f : asFollower) {
                pipe.zrem(followersKey(f.getFolloweeId()), String.valueOf(userId));
            }
            for (Follow f : asFollowee) {
                pipe.zrem(followingKey(f.getFollowerId()), String.valueOf(userId));
            }
            pipe.sync();
        }
    }

    @Override
    public List<Follow> exportAll() {
        return jdbcTemplate.query(
                "SELECT id, follower_id, followee_id, created_at FROM user_follow ORDER BY id",
                this::mapFollow);
    }

    @Override
    @Transactional
    public void importAll(List<Follow> follows) {
        jdbcTemplate.update("DELETE FROM user_follow");
        if (follows == null) {
            return;
        }
        for (Follow f : follows) {
            if (f != null && f.getId() != null) {
                save(f);
            }
        }
    }

    private Long cachedCount(String key) {
        try (Jedis jedis = jedisPool.getResource()) {
            if (!jedis.exists(key)) {
                return null; // 缓存未建 → 回退 MySQL
            }
            return jedis.zcard(key);
        }
    }

    private long countByColumn(Long id, String column) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_follow WHERE " + column + "=?", Integer.class, id);
        return n == null ? 0 : n;
    }

    private Follow mapFollow(ResultSet rs, int rowNum) throws SQLException {
        Follow f = new Follow();
        f.setId(rs.getLong("id"));
        f.setFollowerId(rs.getLong("follower_id"));
        f.setFolloweeId(rs.getLong("followee_id"));
        java.sql.Timestamp ts = rs.getTimestamp("created_at");
        f.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
        return f;
    }

    private String followingKey(Long uid) {
        return "follow:following:" + uid;
    }

    private String followersKey(Long uid) {
        return "follow:followers:" + uid;
    }

    @PreDestroy
    public void close() {
        jedisPool.close();
        log.info("MySQL 关注关系仓库 Redis 连接池已关闭");
    }
}
