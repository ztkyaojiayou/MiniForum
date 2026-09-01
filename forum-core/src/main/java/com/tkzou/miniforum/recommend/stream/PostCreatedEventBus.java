package com.tkzou.miniforum.recommend.stream;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 帖子创建事件总线（进程内多订阅者广播，模拟 Kafka topic "post-created"）
 * <p>
 * 内存发帖（InMemoryPostCreatedProducer）与生产 Kafka 消费（KafkaPostCreatedConsumer）
 * 都 publish 到这里，下游（关注流扇出 / 搜索索引 / 内容管道）subscribe——
 * <b>一份事件、多路消费</b>，@!prod/@prod 通吃。模式对齐行为事件队列 {@link BehaviorEventQueue}。
 */
@Component
public class PostCreatedEventBus {

    private final List<Consumer<PostCreatedEvent>> subscribers = new CopyOnWriteArrayList<>();

    /** 注册订阅者（下游消费者） */
    public void subscribe(Consumer<PostCreatedEvent> consumer) {
        subscribers.add(consumer);
    }

    /** 广播一条帖子创建事件给全部订阅者 */
    public void publish(PostCreatedEvent event) {
        for (Consumer<PostCreatedEvent> consumer : subscribers) {
            consumer.accept(event);
        }
    }
}
