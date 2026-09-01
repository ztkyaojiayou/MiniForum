package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Like;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 行级点赞仓库（生产适配，@Profile("prod")）
 * <p>
 * 对齐存储矩阵"点赞=MySQL 主存储"：行级表 likes，post_id+username 唯一（同一用户对同一帖只点一次）。
 */
@Repository
@Profile("prod")
public class MySqlLikeRepository implements LikeRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlLikeRepository.class);

    private final JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    public MySqlLikeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("MySQL 点赞仓库初始化（行级表 likes）");
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS likes ("
                + "id BIGINT PRIMARY KEY,"
                + "post_id BIGINT NOT NULL,"
                + "username VARCHAR(50) NOT NULL,"
                + "created_at DATETIME NOT NULL,"
                + "UNIQUE KEY uk_like (post_id, username)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public Like save(Like like) {
        if (like.getId() == null) {
            like.setId(idProvider.next("Like"));
        }
        jdbcTemplate.update("INSERT INTO likes(id,post_id,username,created_at) VALUES(?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE created_at=VALUES(created_at)",
                like.getId(), like.getPostId(), like.getUsername(), like.getCreatedAt());
        return like;
    }

    @Override
    public boolean trySaveIfAbsent(Like like) {
        if (like.getId() == null) {
            like.setId(idProvider.next("Like"));
        }
        try {
            // 纯 INSERT（不走 upsert）：uk_like 唯一索引冲突 → DuplicateKeyException → 返回 false
            jdbcTemplate.update("INSERT INTO likes(id,post_id,username,created_at) VALUES(?,?,?,?)",
                    like.getId(), like.getPostId(), like.getUsername(), like.getCreatedAt());
            return true;
        } catch (DuplicateKeyException e) {
            return false; // 同用户并发重复点赞：唯一索引兜底，判重与插入原子合一
        }
    }

    @Override
    public Optional<Like> findByPostIdAndUsername(Long postId, String username) {
        return jdbcTemplate.query("SELECT * FROM likes WHERE post_id=? AND username=?",
                this::mapLike, postId, username).stream().findFirst();
    }

    @Override
    public long countByPostId(Long postId) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM likes WHERE post_id=?", Integer.class, postId);
        return n == null ? 0 : n;
    }

    @Override
    public void delete(Like like) {
        jdbcTemplate.update("DELETE FROM likes WHERE id=?", like.getId());
    }

    @Override
    public void deleteByPostId(Long postId) {
        jdbcTemplate.update("DELETE FROM likes WHERE post_id=?", postId);
    }

    @Override
    public List<Like> findAll() {
        return jdbcTemplate.query("SELECT * FROM likes ORDER BY created_at DESC, id DESC", this::mapLike);
    }

    @Override
    public List<Like> exportAll() {
        return findAll();
    }

    @Override
    @Transactional
    public void importAll(List<Like> likes) {
        jdbcTemplate.update("DELETE FROM likes");
        if (likes == null) {
            return;
        }
        for (Like l : likes) {
            if (l != null && l.getId() != null) {
                save(l);
            }
        }
    }

    private Like mapLike(ResultSet rs, int rowNum) throws SQLException {
        Like l = new Like();
        l.setId(rs.getLong("id"));
        l.setPostId(rs.getLong("post_id"));
        l.setUsername(rs.getString("username"));
        l.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return l;
    }
}
