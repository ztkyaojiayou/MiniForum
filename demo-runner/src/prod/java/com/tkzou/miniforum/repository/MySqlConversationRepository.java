package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Conversation;
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
 * MySQL 行级私信会话仓库（生产适配，@Profile("prod")）
 * <p>
 * 行级表 conversations，uk_pair(user_a,user_b) 唯一约束保证"两人只有一个会话"（多实例不重复建）。
 * findByPair 按字典序归一化后的 user_a/user_b 精确匹配。启用：-Pprod + prod profile + spring.datasource.*。
 */
@Repository
@Profile("prod")
public class MySqlConversationRepository implements ConversationRepository {

    private static final Logger log = LoggerFactory.getLogger(MySqlConversationRepository.class);

    private final JdbcTemplate jdbcTemplate;
    /** ID 生成器（生产 = Snowflake） */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    public MySqlConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        log.info("MySQL 私信会话仓库初始化（行级表 conversations）");
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS conversations ("
                + "id BIGINT PRIMARY KEY,"
                + "user_a VARCHAR(50) NOT NULL,"
                + "user_b VARCHAR(50) NOT NULL,"
                + "last_message_at DATETIME,"
                + "last_message TEXT,"
                + "last_sender VARCHAR(50),"
                + "UNIQUE KEY uk_pair (user_a, user_b)"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    @Override
    public Conversation save(Conversation c) {
        if (c.getId() == null) {
            c.setId(idProvider.next("Conversation"));
        }
        jdbcTemplate.update("INSERT INTO conversations(id,user_a,user_b,last_message_at,last_message,last_sender) "
                        + "VALUES(?,?,?,?,?,?) "
                        + "ON DUPLICATE KEY UPDATE last_message_at=VALUES(last_message_at),"
                        + "last_message=VALUES(last_message),last_sender=VALUES(last_sender)",
                c.getId(), c.getUserA(), c.getUserB(), c.getLastMessageAt(), c.getLastMessage(), c.getLastSender());
        return c;
    }

    @Override
    public Optional<Conversation> findById(Long id) {
        return jdbcTemplate.query("SELECT * FROM conversations WHERE id=?", this::mapConversation, id).stream().findFirst();
    }

    @Override
    public Optional<Conversation> findByPair(String userX, String userY) {
        // 与内存实现语义一致：按字典序归一化后精确匹配 (user_a, user_b)，保证同一对唯一
        String[] pair = normalizePair(userX, userY);
        return jdbcTemplate.query("SELECT * FROM conversations WHERE user_a=? AND user_b=?",
                this::mapConversation, pair[0], pair[1]).stream().findFirst();
    }

    @Override
    public Conversation findOrCreateByPair(String userX, String userY) {
        String[] pair = normalizePair(userX, userY);
        // 插入即幂等：uk_pair 冲突时 user_a=user_a 为 no-op（不动现有会话摘要），随后按 uk 回读真实 id，
        // 修复并发下"upsert 返回新 id、message 写到不存在的 conversation_id"的竞态
        jdbcTemplate.update("INSERT INTO conversations(id,user_a,user_b) VALUES(?,?,?) "
                        + "ON DUPLICATE KEY UPDATE user_a=user_a",
                idProvider.next("Conversation"), pair[0], pair[1]);
        return findByPair(userX, userY).orElseThrow(() -> new IllegalStateException("会话创建失败"));
    }

    @Override
    public List<Conversation> findByUser(String username) {
        return jdbcTemplate.query("SELECT * FROM conversations WHERE user_a=? OR user_b=? ORDER BY last_message_at DESC",
                this::mapConversation, username, username);
    }

    @Override
    public List<Conversation> exportAll() {
        return jdbcTemplate.query("SELECT * FROM conversations ORDER BY id", this::mapConversation);
    }

    @Override
    @Transactional
    public void importAll(List<Conversation> conversations) {
        jdbcTemplate.update("DELETE FROM conversations");
        if (conversations == null) {
            return;
        }
        for (Conversation c : conversations) {
            if (c != null && c.getId() != null) {
                save(c);
            }
        }
    }

    @Override
    public long count() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversations", Integer.class);
        return n == null ? 0 : n;
    }

    /** 字典序归一化：保证 user_a < user_b（与 Conversation.buildKey 一致） */
    private String[] normalizePair(String userX, String userY) {
        return userX.compareTo(userY) <= 0 ? new String[]{userX, userY} : new String[]{userY, userX};
    }

    private Conversation mapConversation(ResultSet rs, int rowNum) throws SQLException {
        Conversation c = new Conversation();
        c.setId(rs.getLong("id"));
        c.setUserA(rs.getString("user_a"));
        c.setUserB(rs.getString("user_b"));
        c.setLastMessageAt(rs.getTimestamp("last_message_at") == null ? null : rs.getTimestamp("last_message_at").toLocalDateTime());
        c.setLastMessage(rs.getString("last_message"));
        c.setLastSender(rs.getString("last_sender"));
        return c;
    }
}
