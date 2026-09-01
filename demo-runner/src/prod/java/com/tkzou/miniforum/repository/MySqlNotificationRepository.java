package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.NotificationType;
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

/**
 * MySQL 行级消息通知仓库（生产适配，@Profile("prod")）
 * <p>
 * 行级表 notifications（recipient_id 索引），未读/已读按 is_read 位过滤，deleteByPostId 级联清理。
 * 启用：-Pprod + prod profile + spring.datasource.*。
 */
@Repository
@Profile("prod")
public class MySqlNotificationRepository implements NotificationRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlNotificationRepository.class);

    private final JdbcTemplate jdbcTemplate;
    /** ID 生成器（生产 = Snowflake） */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    public MySqlNotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("MySQL 消息通知仓库初始化（行级表 notifications）");
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS notifications ("
                + "id BIGINT PRIMARY KEY,"
                + "recipient_id BIGINT NOT NULL,"
                + "actor_id BIGINT,"
                + "actor_username VARCHAR(50),"
                + "type VARCHAR(20) NOT NULL,"
                + "post_id BIGINT,"
                + "content VARCHAR(500),"
                + "is_read TINYINT(1) NOT NULL DEFAULT 0,"
                + "created_at DATETIME NOT NULL,"
                + "KEY idx_recipient (recipient_id)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public Notification save(Notification n) {
        if (n.getId() == null) {
            n.setId(idProvider.next("Notification"));
        }
        jdbcTemplate.update("INSERT INTO notifications(id,recipient_id,actor_id,actor_username,type,post_id,content,is_read,created_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE recipient_id=VALUES(recipient_id),actor_id=VALUES(actor_id),"
                        + "actor_username=VALUES(actor_username),type=VALUES(type),post_id=VALUES(post_id),"
                        + "content=VALUES(content),is_read=VALUES(is_read),created_at=VALUES(created_at)",
                n.getId(), n.getRecipientId(), n.getActorId(), n.getActorUsername(), n.getType().name(), n.getPostId(),
                n.getContent(), n.isRead(), n.getCreatedAt());
        return n;
    }

    @Override
    public List<Notification> findByRecipientId(Long recipientId) {
        return jdbcTemplate.query("SELECT id, recipient_id, actor_id, actor_username, type, post_id, content, is_read, created_at FROM notifications WHERE recipient_id=? ORDER BY created_at DESC, id DESC",
                this::mapNotification, recipientId);
    }

    @Override
    public long countUnread(Long recipientId) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE recipient_id=? AND is_read=0",
                Integer.class, recipientId);
        return n == null ? 0 : n;
    }

    @Override
    public int markAllRead(Long recipientId) {
        return jdbcTemplate.update("UPDATE notifications SET is_read=1 WHERE recipient_id=? AND is_read=0", recipientId);
    }

    @Override
    public void deleteByPostId(Long postId) {
        jdbcTemplate.update("DELETE FROM notifications WHERE post_id=?", postId);
    }

    @Override
    public List<Notification> exportAll() {
        return jdbcTemplate.query("SELECT id, recipient_id, actor_id, actor_username, type, post_id, content, is_read, created_at FROM notifications ORDER BY id", this::mapNotification);
    }

    @Override
    @Transactional
    public void importAll(List<Notification> notifications) {
        jdbcTemplate.update("DELETE FROM notifications");
        if (notifications == null) {
            return;
        }
        for (Notification n : notifications) {
            if (n != null && n.getId() != null) {
                save(n);
            }
        }
    }

    private Notification mapNotification(ResultSet rs, int rowNum) throws SQLException {
        Notification n = new Notification();
        n.setId(rs.getLong("id"));
        n.setRecipientId(rs.getLong("recipient_id"));
        n.setActorId(getLong(rs, "actor_id"));
        n.setActorUsername(rs.getString("actor_username"));
        n.setType(NotificationType.from(rs.getString("type")));
        n.setPostId(getLong(rs, "post_id"));
        n.setContent(rs.getString("content"));
        n.setRead(rs.getBoolean("is_read"));
        n.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
        return n;
    }

    private Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
