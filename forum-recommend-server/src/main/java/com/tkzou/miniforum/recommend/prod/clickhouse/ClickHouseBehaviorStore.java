package com.tkzou.miniforum.recommend.prod.clickhouse;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * ClickHouse 行为日志存储（生产适配，@Profile("prod")）
 * <p>
 * <b>数据流程</b>（docs/数据存储矩阵.md §3）：Kafka "behavior-log" → ClickHouse <b>Kafka Engine 表</b>
 * （自动消费，毫秒级）→ MaterializedView → MergeTree behavior_log。本类用 clickhouse-jdbc <b>读取</b>
 * （离线画像/ItemCF/评估的事实源）；在线画像保持内存（延迟敏感）。
 * <ul>
 *   <li>{@link #initSchema()}：建 behavior_log MergeTree + behavior_log_kafka Kafka Engine + MaterializedView；</li>
 *   <li>查询：findByUserId / findAll / findByPostId / count（对齐 BehaviorLogRepository 读方法）。</li>
 * </ul>
 * 启用：-Pprod + spring.profiles.active=prod + app.rec.clickhouse.host/port + app.rec.kafka.bootstrap-servers。
 */
@Component
@Profile("prod")
public class ClickHouseBehaviorStore {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseBehaviorStore.class);

    private final String jdbcUrl;
    private final String kafkaBootstrap;

    public ClickHouseBehaviorStore(
            @Value("${app.rec.clickhouse.host:localhost}") String host,
            @Value("${app.rec.clickhouse.port:8123}") int port,
            @Value("${app.rec.kafka.bootstrap-servers:localhost:9092}") String kafkaBootstrap) {
        this.jdbcUrl = "jdbc:clickhouse://" + host + ":" + port;
        this.kafkaBootstrap = kafkaBootstrap;
        log.info("ClickHouse 行为日志存储初始化：{}（Kafka 摄入 {}）", jdbcUrl, kafkaBootstrap);
    }

    @PostConstruct
    public void initSchema() {
        // MergeTree：行为全量（按 user_id, timestamp 有序，供画像/ItemCF 聚合）
        execute("CREATE TABLE IF NOT EXISTS behavior_log ("
                + "id UInt64,"
                + "userId UInt64,"
                + "postId UInt64,"
                + "type String,"
                + "timestamp DateTime,"
                + "durationSec Float64,"
                + "scene String,"
                + "expId String"
                + ") ENGINE = MergeTree PARTITION BY toDate(timestamp) ORDER BY (userId, timestamp)");
        // Kafka Engine：直接消费 "behavior-log" topic（JSONEachRow，毫秒级摄入，无需写导入代码）
        execute("CREATE TABLE IF NOT EXISTS behavior_log_kafka ("
                + "id UInt64,"
                + "userId UInt64,"
                + "postId UInt64,"
                + "type String,"
                + "timestamp DateTime,"
                + "durationSec Float64,"
                + "scene String,"
                + "expId String"
                + ") ENGINE = Kafka('" + kafkaBootstrap + "', 'behavior-log', 'mini-forum-clickhouse', 'JSONEachRow')");
        // 物化视图：Kafka 表 → MergeTree
        execute("CREATE MATERIALIZED VIEW IF NOT EXISTS behavior_log_mv TO behavior_log AS SELECT * FROM behavior_log_kafka");
        log.info("ClickHouse 行为日志 schema 就绪（behavior_log + Kafka Engine + 物化视图）");
    }

    /** 某用户全部行为（时间升序，供画像聚合） */
    public List<BehaviorLog> findByUserId(Long userId) {
        return query("SELECT * FROM behavior_log WHERE userId = ? ORDER BY timestamp", userId);
    }

    /** 全部行为（供 ItemCF 构建 / 离线评估） */
    public List<BehaviorLog> findAll() {
        return query("SELECT * FROM behavior_log ORDER BY timestamp", (Object) null);
    }

    /** 某帖全部行为（供热度/时长信号） */
    public List<BehaviorLog> findByPostId(Long postId) {
        return query("SELECT * FROM behavior_log WHERE postId = ? ORDER BY timestamp", postId);
    }

    /** 行为总数（供"行为不足跳过评估"判断） */
    public long count() {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement("SELECT count() FROM behavior_log");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (Exception e) {
            log.warn("ClickHouse count 失败：{}", e.getMessage());
            return 0;
        }
    }

    private List<BehaviorLog> query(String sql, Object param) {
        List<BehaviorLog> result = new ArrayList<>();
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            if (param != null) {
                ps.setLong(1, (Long) param);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    BehaviorLog b = new BehaviorLog();
                    b.setId(rs.getLong("id"));
                    b.setUserId(rs.getLong("userId"));
                    b.setPostId(rs.getLong("postId"));
                    b.setType(parseType(rs.getString("type")));
                    LocalDateTime ts = rs.getTimestamp("timestamp").toLocalDateTime();
                    b.setTimestamp(ts);
                    b.setDurationSec(rs.getDouble("durationSec"));
                    b.setScene(rs.getString("scene"));
                    b.setExpId(rs.getString("expId"));
                    result.add(b);
                }
            }
        } catch (Exception e) {
            log.warn("ClickHouse 查询失败：{}", e.getMessage());
        }
        return result;
    }

    private void execute(String sql) {
        try (Connection conn = connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        } catch (Exception e) {
            log.warn("ClickHouse DDL 失败：{}", e.getMessage());
        }
    }

    private Connection connect() throws Exception {
        Class.forName("com.clickhouse.jdbc.ClickHouseDriver");
        return DriverManager.getConnection(jdbcUrl);
    }

    private BehaviorType parseType(String type) {
        if (type == null) {
            return null;
        }
        try {
            return BehaviorType.valueOf(type);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
