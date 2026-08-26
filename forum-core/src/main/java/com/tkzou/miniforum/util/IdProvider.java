package com.tkzou.miniforum.util;

/**
 * ID 生成器（生产级全局唯一 ID）
 * <p>
 * 替代各实体的静态 AtomicLong：演示用 {@link EntityIdProvider}（@Profile("!prod")，委托各实体
 * nextId()，行为不变）；生产用 {@link SnowflakeIdProvider}（@Profile("prod")，全局唯一 + 趋势递增 + 带时间戳）。
 * 各 repository 以"字段注入 + 默认 EntityIdProvider"方式持有，测试无 Spring 时自动落到实体原生成器。
 */
public interface IdProvider {

    /** 为指定实体生成下一个 ID（entity 如 "Post"/"User"/"Follow"/"BehaviorLog"） */
    long next(String entity);

    /** 持久化恢复后设置最小 ID（新 ID 保证 ≥ min，避免与历史数据冲突；演示实现即实体 resetIdGenerator） */
    void reset(String entity, long min);
}
