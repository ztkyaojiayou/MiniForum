package com.tkzou.miniforum.recommend.stream;

/**
 * 帖子创建事件发布器接口
 * <p>
 * 发帖落库后触发，生产形态经 Kafka topic "post-created" 异步下发下游
 * （搜索索引 / feed 扇出 / 内容管道 / 推荐冷启动）。
 * 默认使用内存实现（InMemoryPostCreatedNotifier，@Profile("!prod")），
 * 生产 profile 使用 Kafka 实现（prod.kafka.KafkaPostCreatedProducer）。
 */
public interface PostCreatedNotifier {

    /** 发布一条帖子创建事件 */
    void notify(PostCreatedEvent event);
}
