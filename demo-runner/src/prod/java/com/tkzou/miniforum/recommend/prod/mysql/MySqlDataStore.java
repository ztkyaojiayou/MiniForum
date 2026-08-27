package com.tkzou.miniforum.recommend.prod.mysql;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tkzou.miniforum.entity.Conversation;
import com.tkzou.miniforum.entity.Message;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.SearchRecord;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.repository.ConversationRepository;
import com.tkzou.miniforum.repository.MessageRepository;
import com.tkzou.miniforum.repository.NotificationRepository;
import com.tkzou.miniforum.repository.SearchRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * MySQL 持久化（生产适配，@Profile("prod") 激活，替代 JSON 文件 DataStore）
 * <p>
 * <b>数据流程</b>：启动时建表 mini_store(store_key, payload) 并从 MySQL 恢复<b>尚未行级化</b>的仓库；
 * 运行时每 30 秒/关闭时将它们的 exportAll() 序列化为 JSON blob upsert 到 mini_store。
 * <b>已行级化的仓库</b>（posts/users/comments/likes/follows/favorites）由各自的 MySql*Repository
 * （@PostConstruct 建表 + 行级读写）自管，不再走本快照——对齐"MySQL=事实/主存储"（docs/数据存储矩阵.md）。
 * <p>
 * 启用：构建 `-Pprod` + 运行 `--spring.profiles.active=prod` + 配置 spring.datasource.url/username/password。
 */
@Component
@Profile("prod")
public class MySqlDataStore implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MySqlDataStore.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    // 已行级化的仓库（posts/users/comments/likes/follows/favorites）由各自的 MySql*Repository 自管，此处不再持有
    private final NotificationRepository notificationRepository;
    private final SearchRecordRepository searchRecordRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final BehaviorLogRepository behaviorLogRepository;

    /** 防止定时保存早于启动加载，覆盖已有数据 */
    private final AtomicBoolean loaded = new AtomicBoolean(false);

    public MySqlDataStore(JdbcTemplate jdbcTemplate,
                          ObjectMapper objectMapper,
                          NotificationRepository notificationRepository,
                          SearchRecordRepository searchRecordRepository,
                          ConversationRepository conversationRepository,
                          MessageRepository messageRepository,
                          BehaviorLogRepository behaviorLogRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.notificationRepository = notificationRepository;
        this.searchRecordRepository = searchRecordRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.behaviorLogRepository = behaviorLogRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        initSchema();
        loadAll();
        loaded.set(true);
        log.info("MySQL 持久化加载完成");
    }

    /** 建表（幂等） */
    private void initSchema() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS mini_store ("
                + "store_key VARCHAR(64) PRIMARY KEY,"
                + "payload MEDIUMTEXT NOT NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    /** 全量保存：各仓库 exportAll() → JSON blob upsert 到 mini_store */
    public synchronized void saveAll() {
        if (!loaded.get()) {
            return;
        }
        // 已行级化的仓库（posts/users/comments/likes/follows/favorites）由各自 MySql*Repository 自管，不再快照
        save("notifications", notificationRepository.exportAll());
        save("search-records", searchRecordRepository.exportAll());
        save("conversations", conversationRepository.exportAll());
        save("messages", messageRepository.exportAll());
        save("behavior-log", behaviorLogRepository.exportAll());
    }

    private void save(String key, List<?> list) {
        try {
            String payload = objectMapper.writeValueAsString(list);
            jdbcTemplate.update(
                    "INSERT INTO mini_store(store_key, payload) VALUES(?, ?) "
                            + "ON DUPLICATE KEY UPDATE payload = VALUES(payload)",
                    key, payload);
        } catch (Exception e) {
            log.warn("MySQL 保存 {} 失败：{}", key, e.getMessage());
        }
    }

    /** 全量加载：从 mini_store 恢复各仓库，并复位 ID 生成器 */
    public synchronized void loadAll() {
        // 已行级化的仓库由各自 MySql*Repository 启动即读行级表，不再从 mini_store 恢复
        load("notifications", Notification.class, notificationRepository::importAll,
                Notification::getId, Notification::resetIdGenerator);
        load("search-records", SearchRecord.class, searchRecordRepository::importAll,
                SearchRecord::getId, SearchRecord::resetIdGenerator);
        load("conversations", Conversation.class, conversationRepository::importAll,
                Conversation::getId, Conversation::resetIdGenerator);
        load("messages", Message.class, messageRepository::importAll, Message::getId, Message::resetIdGenerator);
        load("behavior-log", BehaviorLog.class, behaviorLogRepository::importAll,
                BehaviorLog::getId, BehaviorLog::resetIdGenerator);
    }

    private <T> void load(String key, Class<T> clazz, Consumer<List<T>> importAll,
                          Function<T, Long> getId, Consumer<Long> resetId) {
        try {
            String payload = jdbcTemplate.queryForObject(
                    "SELECT payload FROM mini_store WHERE store_key = ?", String.class, key);
            if (payload == null) {
                return;
            }
            List<T> list = objectMapper.readValue(payload,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
            importAll.accept(list);
            list.stream().map(getId).filter(Objects::nonNull).max(Long::compareTo).ifPresent(resetId);
        } catch (EmptyResultDataAccessException e) {
            // 表存在但尚无该 key
        } catch (Exception e) {
            log.warn("MySQL 加载 {} 失败：{}", key, e.getMessage());
        }
    }

    /** 调度模式：local=@Scheduled 自调度（演示默认）/ xxl=由 XXL-Job 派发（生产，@Scheduled 空转防双跑） */
    @Value("${app.scheduling.mode:local}")
    private String schedulingMode;

    /** 定时保存（默认每 30 秒）。生产由 XXL-Job 派发 doScheduledSave，此处空转防双跑 */
    @Scheduled(fixedDelayString = "${app.persistence.interval-ms:30000}")
    public void scheduledSave() {
        if ("xxl".equals(schedulingMode)) {
            return;
        }
        doScheduledSave();
    }

    /** 保存快照（演示自调度与 XXL-Job handler 共用入口） */
    public void doScheduledSave() {
        saveAll();
    }

    /** 应用关闭时保存 */
    @PreDestroy
    public void shutdown() {
        saveAll();
        log.info("MySQL 持久化已保存（关闭前）");
    }
}
