package com.tkzou.miniforum.recommend.mq;

import com.tkzou.miniforum.recommend.mq.producer.PostCreatedProducer;

/**
 * 发帖事件 Outbox 存储
 * <p>
 * 保证发帖事件"必达"：入队（{@link #enqueue}）后由 Relayer 兜底投递到 Kafka，失败重试、不丢。
 * 双实现：
 * <ul>
 *   <li>内存 {@link InMemoryOutboxStore}（@Profile("!prod")）——同步委托 {@link PostCreatedProducer}，保持演示闭环；</li>
 *   <li>MySQL {@code MySqlOutboxStore}（@Profile("prod")，demo-runner/src/prod）——写 post_outbox 表
 *       （status=PENDING），定时 Relayer 轮询 → PostCreatedProducer（Kafka 实现）投递 → status=DONE。</li>
 * </ul>
 */
public interface OutboxStore {

    /** 入队一条帖子创建事件（演示 = 同步发布；生产 = 落 outbox 表待投递） */
    void enqueue(PostCreatedEvent event);
}
