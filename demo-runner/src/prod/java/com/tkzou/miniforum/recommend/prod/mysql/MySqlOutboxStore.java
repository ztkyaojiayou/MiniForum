package com.tkzou.miniforum.recommend.prod.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.recommend.stream.OutboxStore;
import com.tkzou.miniforum.recommend.stream.PostCreatedEvent;
import com.tkzou.miniforum.recommend.stream.PostCreatedNotifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;

/**
 * MySQL 发帖事件 Outbox（生产适配，@Profile("prod") 激活，替代内存 OutboxStore）
 * <p>
 * <b>数据流程</b>：发帖 → {@link #enqueue} 把事件写 post_outbox 表（status=PENDING，持久化保证不丢）
 * → 定时 Relayer 轮询 PENDING → {@link PostCreatedNotifier#notify}（prod = KafkaPostCreatedProducer 发 Kafka
 * "post-created"，ack=1 + 生产者内置重试）→ 成功后 status=DONE；
 * 投递异常保留 PENDING 下轮重试（at-least-once，下游 fanout/冷启幂等）。
 * <p>
 * 与内存 OutboxStore 的区别：演示同步发布（不落表）；本实现持久化 + 重试，保证"入 outbox 后事件必达"。
 * 启用：-Pprod 构建 + spring.profiles.active=prod + spring.datasource.* 配置。
 */
@Component
@Profile("prod")
public class MySqlOutboxStore implements OutboxStore {

    /** Outbox 投递状态（P1-20 String 裸值 → 枚举化；落库仍为 name() 字符串） */
    private enum OutboxStatus {
        PENDING, DONE
    }

    private static final Logger log = LoggerFactory.getLogger(MySqlOutboxStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final PostCreatedNotifier postCreatedNotifier;
    private final ObjectMapper objectMapper;
    private final int batchSize;

    public MySqlOutboxStore(JdbcTemplate jdbcTemplate,
                            PostCreatedNotifier postCreatedNotifier,
                            ObjectMapper objectMapper,
                            @Value("${app.outbox.batch-size:100}") int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.postCreatedNotifier = postCreatedNotifier;
        this.objectMapper = objectMapper;
        this.batchSize = batchSize;
    }

    @PostConstruct
    public void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS post_outbox ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                + "post_id BIGINT NOT NULL,"
                + "status VARCHAR(16) NOT NULL DEFAULT 'PENDING',"
                + "payload TEXT NOT NULL,"
                + "created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        log.info("MySQL 发帖事件 Outbox 初始化完成（轮询间隔可配 app.outbox.poll-ms）");
    }

    @Override
    public void enqueue(PostCreatedEvent event) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO post_outbox(post_id, status, payload) VALUES(?, '" + OutboxStatus.PENDING.name() + "', ?)",
                    event.getPostId(), objectMapper.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("Outbox 写入失败：postId={}", event.getPostId(), e);
        }
    }

    /** 定时 Relayer：轮询 PENDING → 投递 Kafka → 标记 DONE（失败留 PENDING 重试） */
    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:1000}")
    public void relay() {
        List<Map<String, Object>> pending;
        try {
            pending = jdbcTemplate.queryForList(
                    "SELECT id, post_id, payload FROM post_outbox WHERE status = '" + OutboxStatus.PENDING.name() + "' ORDER BY id LIMIT " + batchSize);
        } catch (Exception e) {
            log.warn("Outbox 轮询失败", e);
            return;
        }
        for (Map<String, Object> row : pending) {
            long id = ((Number) row.get("id")).longValue();
            long postId = ((Number) row.get("post_id")).longValue();
            try {
                PostCreatedEvent event = objectMapper.readValue((String) row.get("payload"), PostCreatedEvent.class);
                postCreatedNotifier.notify(event);
                jdbcTemplate.update("UPDATE post_outbox SET status = '" + OutboxStatus.DONE.name() + "' WHERE id = ?", id);
            } catch (Exception e) {
                // 保留 PENDING，下轮重试（at-least-once；下游 fanout/冷启以 postId 幂等）
                log.warn("Outbox 投递失败，保留 PENDING 重试：id={} postId={}", id, postId, e);
            }
        }
    }
}
