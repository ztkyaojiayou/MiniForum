package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Favorite;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 行级收藏仓库（生产适配，@Profile("prod")）
 * <p>
 * 对齐存储矩阵"收藏=MySQL 主存储"：行级表 favorites，post_id+username 唯一。
 */
@Repository
@Profile("prod")
public class MySqlFavoriteRepository implements FavoriteRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlFavoriteRepository.class);

    private final JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    public MySqlFavoriteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("MySQL 收藏仓库初始化（行级表 favorites）");
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS favorites ("
                + "id BIGINT PRIMARY KEY,"
                + "post_id BIGINT NOT NULL,"
                + "username VARCHAR(50) NOT NULL,"
                + "created_at DATETIME NOT NULL,"
                + "UNIQUE KEY uk_fav (post_id, username),"
                + "KEY idx_username (username, created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        // 存量表补索引（findPostIdsByUsername 的 username 分支走索引，避免全表扫）：幂等补建
        ensureIndex("favorites", "idx_username", "ALTER TABLE favorites ADD INDEX idx_username (username, created_at)");
    }

    /** 幂等补索引：information_schema 判定不存在时才执行 ALTER */
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
    public Favorite save(Favorite favorite) {
        if (favorite.getId() == null) {
            favorite.setId(idProvider.next("Favorite"));
        }
        jdbcTemplate.update("INSERT INTO favorites(id,post_id,username,created_at) VALUES(?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE created_at=VALUES(created_at)",
                favorite.getId(), favorite.getPostId(), favorite.getUsername(), favorite.getCreatedAt());
        return favorite;
    }

    @Override
    public Optional<Favorite> findByPostIdAndUsername(Long postId, String username) {
        return jdbcTemplate.query("SELECT id, post_id, username, created_at FROM favorites WHERE post_id=? AND username=?",
                this::mapFavorite, postId, username).stream().findFirst();
    }

    @Override
    public long countByPostId(Long postId) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM favorites WHERE post_id=?", Integer.class, postId);
        return n == null ? 0 : n;
    }

    @Override
    public void delete(Favorite favorite) {
        jdbcTemplate.update("DELETE FROM favorites WHERE id=?", favorite.getId());
    }

    @Override
    public void deleteByPostId(Long postId) {
        jdbcTemplate.update("DELETE FROM favorites WHERE post_id=?", postId);
    }

    @Override
    public List<Long> findPostIdsByUsername(String username) {
        return jdbcTemplate.queryForList("SELECT post_id FROM favorites WHERE username=? ORDER BY created_at DESC, id DESC",
                Long.class, username);
    }

    @Override
    public List<Favorite> findAll() {
        return jdbcTemplate.query("SELECT id, post_id, username, created_at FROM favorites ORDER BY created_at DESC, id DESC", this::mapFavorite);
    }

    @Override
    public List<Favorite> exportAll() {
        return findAll();
    }

    @Override
    @Transactional
    public void importAll(List<Favorite> favorites) {
        jdbcTemplate.update("DELETE FROM favorites");
        if (favorites == null) {
            return;
        }
        for (Favorite f : favorites) {
            if (f != null && f.getId() != null) {
                save(f);
            }
        }
    }

    private Favorite mapFavorite(ResultSet rs, int rowNum) throws SQLException {
        Favorite f = new Favorite();
        f.setId(rs.getLong("id"));
        f.setPostId(rs.getLong("post_id"));
        f.setUsername(rs.getString("username"));
        f.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        return f;
    }
}
