package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Comment;
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
 * MySQL 行级评论仓库（生产适配，@Profile("prod")）
 * <p>
 * 对齐存储矩阵"评论=MySQL 主存储"：行级表 comments，post_id 索引（楼中楼用 parent_id）。
 */
@Repository
@Profile("prod")
public class MySqlCommentRepository implements CommentRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlCommentRepository.class);

    private final JdbcTemplate jdbcTemplate;
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    public MySqlCommentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("MySQL 评论仓库初始化（行级表 comments）");
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS comments ("
                + "id BIGINT PRIMARY KEY,"
                + "post_id BIGINT NOT NULL,"
                + "author VARCHAR(50) NOT NULL,"
                + "content TEXT NOT NULL,"
                + "created_at DATETIME NOT NULL,"
                + "like_count BIGINT NOT NULL DEFAULT 0,"
                + "parent_id BIGINT,"
                + "KEY idx_post (post_id, created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public Comment save(Comment comment) {
        if (comment.getId() == null) {
            comment.setId(idProvider.next("Comment"));
        }
        jdbcTemplate.update("INSERT INTO comments(id,post_id,author,content,created_at,like_count,parent_id) "
                        + "VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE content=VALUES(content),like_count=VALUES(like_count),parent_id=VALUES(parent_id)",
                comment.getId(), comment.getPostId(), comment.getAuthor(), comment.getContent(),
                comment.getCreatedAt(), comment.getLikeCount(), comment.getParentId());
        return comment;
    }

    @Override
    public Optional<Comment> findById(Long id) {
        return jdbcTemplate.query("SELECT id, post_id, author, content, created_at, like_count, parent_id FROM comments WHERE id=?", this::mapComment, id).stream().findFirst();
    }

    @Override
    public List<Comment> findByPostId(Long postId) {
        return jdbcTemplate.query("SELECT id, post_id, author, content, created_at, like_count, parent_id FROM comments WHERE post_id=? ORDER BY created_at ASC, id ASC",
                this::mapComment, postId);
    }

    @Override
    public long countByPostId(Long postId) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comments WHERE post_id=?", Integer.class, postId);
        return n == null ? 0 : n;
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM comments WHERE id=?", id);
    }

    @Override
    public void deleteByPostId(Long postId) {
        jdbcTemplate.update("DELETE FROM comments WHERE post_id=?", postId);
    }

    @Override
    public List<Comment> findAll() {
        return jdbcTemplate.query("SELECT id, post_id, author, content, created_at, like_count, parent_id FROM comments ORDER BY created_at DESC, id DESC", this::mapComment);
    }

    @Override
    public long count() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM comments", Integer.class);
        return n == null ? 0 : n;
    }

    @Override
    public List<Comment> exportAll() {
        return findAll();
    }

    @Override
    @Transactional
    public void importAll(List<Comment> comments) {
        jdbcTemplate.update("DELETE FROM comments");
        if (comments == null) {
            return;
        }
        for (Comment c : comments) {
            if (c != null && c.getId() != null) {
                save(c);
            }
        }
    }

    private Comment mapComment(ResultSet rs, int rowNum) throws SQLException {
        Comment c = new Comment();
        c.setId(rs.getLong("id"));
        c.setPostId(rs.getLong("post_id"));
        c.setAuthor(rs.getString("author"));
        c.setContent(rs.getString("content"));
        c.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        c.setLikeCount(rs.getLong("like_count"));
        long parent = rs.getLong("parent_id");
        c.setParentId(rs.wasNull() ? null : parent);
        return c;
    }
}
