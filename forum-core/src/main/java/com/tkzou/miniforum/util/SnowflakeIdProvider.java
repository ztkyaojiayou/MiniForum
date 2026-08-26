package com.tkzou.miniforum.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Snowflake ID 生成器（生产适配，@Profile("prod")）
 * <p>
 * 64-bit = <b>41 位时间戳(ms) + 10 位 workerId + 12 位序列</b>：全局唯一、趋势递增、自带时间戳
 * （ID 大小可当游标，关注流 max_id/since_id 天然可用）。多实例各自配不同 workerId 即不冲突。
 * <p>
 * {@link #reset(String, long)}：记录各实体最小 ID，生成低于 min 时取 min——兼容持久化恢复的旧数据
 * （演示历史为小 ID，Snowflake 天然大于，clamp 基本不触发，仅作安全下限）。
 * 启用：-Pprod 构建 + spring.profiles.active=prod + app.id.worker-id（单机默认 0）。
 */
@Component
@Profile("prod")
public class SnowflakeIdProvider implements IdProvider {

    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdProvider.class);

    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);
    private static final long WORKER_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    /** 自定义纪元（2024-01-01 00:00:00 UTC），比 Twitter 默认更近，留更大时间跨度 */
    private static final long EPOCH = 1704067200000L;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;
    /** 各实体最小 ID（reset 后新 ID 不低于此值） */
    private final Map<String, Long> minIds = new ConcurrentHashMap<>();

    public SnowflakeIdProvider(@Value("${app.id.worker-id:0}") long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId 超范围：" + workerId);
        }
        this.workerId = workerId;
        log.info("Snowflake ID 生成器初始化，workerId={}", workerId);
    }

    @Override
    public synchronized long next(String entity) {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            // 时钟回拨：退避等待追上（容忍短回拨）
            timestamp = waitForNextMillis(lastTimestamp);
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;

        long id = ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (workerId << WORKER_SHIFT)
                | sequence;
        Long min = minIds.get(entity);
        if (min != null && id < min) {
            id = min; // 安全下限（历史数据恢复后不生成更小 ID）
        }
        return id;
    }

    @Override
    public void reset(String entity, long min) {
        minIds.put(entity, min);
    }

    private long waitForNextMillis(long lastTimestamp) {
        long ts = System.currentTimeMillis();
        while (ts <= lastTimestamp) {
            ts = System.currentTimeMillis();
        }
        return ts;
    }
}
