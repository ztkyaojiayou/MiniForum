package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Message;
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
 * MySQL 行级私信消息仓库（生产适配，@Profile("prod")）
 * <p>
 * 对齐存储矩阵"消息=MySQL 主存储"：行级表 messages（conversation_id 索引），
 * 未读/已读按 is_read 位过滤（sender 排除自己，与内存语义一致）。
 * save 用 INSERT ... ON DUPLICATE KEY UPDATE（改+增合一）。启用：-Pprod + prod profile + spring.datasource.*。
 */
@Repository
@Profile("prod")
public class MySqlMessageRepository implements MessageRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlMessageRepository.class);

    private final JdbcTemplate jdbcTemplate;
    /** ID 生成器（生产 = Snowflake） */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    public MySqlMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("MySQL 私信消息仓库初始化（行级表 messages）");
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS messages ("
                + "id BIGINT PRIMARY KEY,"
                + "conversation_id BIGINT NOT NULL,"
                + "sender VARCHAR(50) NOT NULL,"
                + "sender_id BIGINT,"
                + "receiver VARCHAR(50) NOT NULL,"
                + "receiver_id BIGINT,"
                + "content TEXT NOT NULL,"
                + "created_at DATETIME NOT NULL,"
                + "is_read TINYINT(1) NOT NULL DEFAULT 0,"
                + "KEY idx_conversation (conversation_id),"
                + "KEY idx_receiver (receiver)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public Message save(Message m) {
        if (m.getId() == null) {
            m.setId(idProvider.next("Message"));
        }
        jdbcTemplate.update("INSERT INTO messages(id,conversation_id,sender,sender_id,receiver,receiver_id,content,created_at,is_read) "
                        + "VALUES(?,?,?,?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE conversation_id=VALUES(conversation_id),sender=VALUES(sender),"
                        + "sender_id=VALUES(sender_id),receiver=VALUES(receiver),receiver_id=VALUES(receiver_id),"
                        + "content=VALUES(content),created_at=VALUES(created_at),is_read=VALUES(is_read)",
                m.getId(), m.getConversationId(), m.getSender(), m.getSenderId(), m.getReceiver(), m.getReceiverId(),
                m.getContent(), m.getCreatedAt(), m.isRead());
        return m;
    }

    @Override
    public List<Message> findByConversationId(Long conversationId) {
        return jdbcTemplate.query("SELECT * FROM messages WHERE conversation_id=? ORDER BY created_at, id",
                this::mapMessage, conversationId);
    }

    @Override
    public long countUnread(Long conversationId, String myUsername) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE conversation_id=? AND is_read=0 AND sender<>?",
                Integer.class, conversationId, myUsername);
        return n == null ? 0 : n;
    }

    @Override
    public int markAllRead(Long conversationId, String myUsername) {
        return jdbcTemplate.update(
                "UPDATE messages SET is_read=1 WHERE conversation_id=? AND is_read=0 AND sender<>?",
                conversationId, myUsername);
    }

    @Override
    public long countUnreadForUser(String myUsername) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE receiver=? AND is_read=0",
                Integer.class, myUsername);
        return n == null ? 0 : n;
    }

    @Override
    public List<Message> exportAll() {
        return jdbcTemplate.query("SELECT * FROM messages ORDER BY id", this::mapMessage);
    }

    @Override
    @Transactional
    public void importAll(List<Message> messages) {
        jdbcTemplate.update("DELETE FROM messages");
        if (messages == null) {
            return;
        }
        for (Message m : messages) {
            if (m != null && m.getId() != null) {
                save(m);
            }
        }
    }

    @Override
    public long count() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM messages", Integer.class);
        return n == null ? 0 : n;
    }

    private Message mapMessage(ResultSet rs, int rowNum) throws SQLException {
        Message m = new Message();
        m.setId(rs.getLong("id"));
        m.setConversationId(rs.getLong("conversation_id"));
        m.setSender(rs.getString("sender"));
        m.setSenderId(getLong(rs, "sender_id"));
        m.setReceiver(rs.getString("receiver"));
        m.setReceiverId(getLong(rs, "receiver_id"));
        m.setContent(rs.getString("content"));
        m.setCreatedAt(rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime());
        m.setRead(rs.getBoolean("is_read"));
        return m;
    }

    private Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }
}
