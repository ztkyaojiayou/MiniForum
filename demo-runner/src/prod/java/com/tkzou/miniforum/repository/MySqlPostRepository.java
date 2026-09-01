package com.tkzou.miniforum.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.PostStatus;
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

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL 行级帖子仓库（生产适配，@Profile("prod")）
 * <p>
 * 对齐存储矩阵"帖子=MySQL 主存储"：行级表 posts（tags/topics 存 JSON 列），
 * 按作者查询走 author_id 索引（替代内存版二级索引），findAll 按 created_at 倒序。
 * save 用 INSERT ... ON DUPLICATE KEY UPDATE（改+增合一，与内存语义一致）。
 * 启用：-Pprod + spring.profiles.active=prod + spring.datasource.*。
 */
@Repository
@Profile("prod")
public class MySqlPostRepository implements PostRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlPostRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    /** ID 生成器（生产 = Snowflake） */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    public MySqlPostRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        log.info("MySQL 帖子仓库初始化（行级表 posts）");
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS posts ("
                + "id BIGINT PRIMARY KEY,"
                + "title VARCHAR(200) NOT NULL DEFAULT '',"
                + "content TEXT NOT NULL,"
                + "author VARCHAR(50) NOT NULL,"
                + "author_id BIGINT,"
                + "created_at DATETIME NOT NULL,"
                + "tags TEXT,"
                + "topics TEXT,"
                + "category VARCHAR(20),"
                + "status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',"
                + "like_count BIGINT NOT NULL DEFAULT 0,"
                + "view_count BIGINT NOT NULL DEFAULT 0,"
                + "deleted TINYINT(1) NOT NULL DEFAULT 0,"
                + "deleted_at DATETIME,"
                + "original_post_id BIGINT,"
                + "original_author_id BIGINT,"
                + "original_author VARCHAR(50),"
                + "KEY idx_author (author_id),"
                + "KEY idx_created (created_at)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public Post save(Post post) {
        if (post.getId() == null) {
            post.setId(idProvider.next("Post"));
        }
        jdbcTemplate.update("INSERT INTO posts(id,title,content,author,author_id,created_at,tags,topics,category,"
                        + "status,like_count,view_count,deleted,deleted_at,original_post_id,original_author_id,original_author) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE title=VALUES(title),content=VALUES(content),author=VALUES(author),"
                        + "author_id=VALUES(author_id),tags=VALUES(tags),topics=VALUES(topics),category=VALUES(category),"
                        + "status=VALUES(status),like_count=VALUES(like_count),view_count=VALUES(view_count),"
                        + "deleted=VALUES(deleted),deleted_at=VALUES(deleted_at),"
                        + "original_post_id=VALUES(original_post_id),original_author_id=VALUES(original_author_id),"
                        + "original_author=VALUES(original_author)",
                post.getId(), post.getTitle(), post.getContent(), post.getAuthor(), post.getAuthorId(), post.getCreatedAt(),
                toJson(post.getTags()), toJson(post.getTopics()), post.getCategory(), post.getStatus().name(),
                post.getLikeCount(), post.getViewCount(), post.isDeleted(), post.getDeletedAt(),
                post.getOriginalPostId(), post.getOriginalAuthorId(), post.getOriginalAuthor());
        return post;
    }

    @Override
    public Optional<Post> findById(Long id) {
        return jdbcTemplate.query("SELECT id, title, content, author, author_id, created_at, tags, topics, category, status, like_count, view_count, deleted, deleted_at, original_post_id, original_author_id, original_author FROM posts WHERE id=?", this::mapPost, id).stream().findFirst();
    }

    @Override
    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM posts WHERE id=?", id);
    }

    @Override
    public List<Post> findAll() {
        return jdbcTemplate.query("SELECT id, title, content, author, author_id, created_at, tags, topics, category, status, like_count, view_count, deleted, deleted_at, original_post_id, original_author_id, original_author FROM posts ORDER BY created_at DESC, id DESC", this::mapPost);
    }

    @Override
    public List<Post> findByAuthorId(Long authorId) {
        return jdbcTemplate.query("SELECT id, title, content, author, author_id, created_at, tags, topics, category, status, like_count, view_count, deleted, deleted_at, original_post_id, original_author_id, original_author FROM posts WHERE author_id=? ORDER BY created_at DESC, id DESC",
                this::mapPost, authorId);
    }

    @Override
    public List<Post> exportAll() {
        return jdbcTemplate.query("SELECT id, title, content, author, author_id, created_at, tags, topics, category, status, like_count, view_count, deleted, deleted_at, original_post_id, original_author_id, original_author FROM posts ORDER BY id", this::mapPost);
    }

    @Override
    @Transactional
    public void importAll(List<Post> posts) {
        jdbcTemplate.update("DELETE FROM posts");
        if (posts == null) {
            return;
        }
        for (Post p : posts) {
            if (p != null && p.getId() != null) {
                save(p);
            }
        }
    }

    @Override
    public long incrementLikeCount(Long postId, int delta) {
        jdbcTemplate.update("UPDATE posts SET like_count = GREATEST(like_count + ?, 0) WHERE id = ?", delta, postId);
        Integer n = jdbcTemplate.queryForObject("SELECT like_count FROM posts WHERE id = ?", Integer.class, postId);
        return n == null ? 0 : n;
    }

    @Override
    public long incrementViewCount(Long postId, int delta) {
        jdbcTemplate.update("UPDATE posts SET view_count = GREATEST(view_count + ?, 0) WHERE id = ?", delta, postId);
        Integer n = jdbcTemplate.queryForObject("SELECT view_count FROM posts WHERE id = ?", Integer.class, postId);
        return n == null ? 0 : n;
    }

    @Override
    public long count() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM posts", Integer.class);
        return n == null ? 0 : n;
    }

    private Post mapPost(ResultSet rs, int rowNum) throws SQLException {
        Post p = new Post();
        p.setId(rs.getLong("id"));
        p.setTitle(rs.getString("title"));
        p.setContent(rs.getString("content"));
        p.setAuthor(rs.getString("author"));
        p.setAuthorId(getLong(rs, "author_id"));
        p.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
        p.setTags(fromJson(rs.getString("tags")));
        p.setTopics(fromJson(rs.getString("topics")));
        p.setCategory(rs.getString("category"));
        p.setStatus(PostStatus.from(rs.getString("status")));
        p.setLikeCount(rs.getLong("like_count"));
        p.setViewCount(rs.getLong("view_count"));
        p.setDeleted(rs.getBoolean("deleted"));
        p.setDeletedAt(rs.getTimestamp("deleted_at") == null ? null : rs.getTimestamp("deleted_at").toLocalDateTime());
        p.setOriginalPostId(getLong(rs, "original_post_id"));
        p.setOriginalAuthorId(getLong(rs, "original_author_id"));
        p.setOriginalAuthor(rs.getString("original_author"));
        return p;
    }

    private Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? new ArrayList<>() : list);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
}
