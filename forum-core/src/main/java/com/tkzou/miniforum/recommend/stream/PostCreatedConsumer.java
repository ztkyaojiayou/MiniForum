package com.tkzou.miniforum.recommend.stream;

/**
 * 帖子创建事件订阅者接口（统一的一路消费）
 * <p>
 * 所有下行消费者（关注流扇出 / 搜索索引 / 冷启动流量池 / 未来内容管道等）实现本接口，
 * 由 {@code PostCreatedConsumerRegistrar} 统一收集并注册到 {@link PostCreatedEventBus}——
 * 订阅关系集中在装配器，新增消费方只需新建 {@code @Component} 实现本接口，现有一处不碰。
 * <p>
 * <b>对齐 Kafka consumer 心智</b>：每个实现 = 一个独立消费组，{@link #name()} 是该组的标识
 * （用于日志 / 监控 / 顺序控制）。@!prod/@prod 通吃：内存发帖（InMemoryPostCreatedProducer）
 * 与生产 Kafka 消费（KafkaPostCreatedIngestor）最终都 publish 到总线，本接口实现者无需感知来源。
 */
public interface PostCreatedConsumer {

    /** 消费组标识（如 "follow-fanout" / "search-index" / "traffic-pool"） */
    String name();

    /** 收到帖子创建事件后的处理（每个实现 = 一路下游消费） */
    void consume(PostCreatedEvent event);
}