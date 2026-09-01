package com.tkzou.miniforum.recommend.stream;

import org.springframework.beans.factory.annotation.Autowired;
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
 * <p>
 * <b>消费者装配收进总线</b>：{@code @Autowired} 构造器用 Spring 的 {@code List<PostCreatedConsumer>}
 * 自动收集全部实现并逐个订阅（新增 {@code @Component} 实现即自动接入，零改动）；无参构造供测试/手动订阅。
 */
@Component
public class PostCreatedEventBus {

    private final List<Consumer<PostCreatedEvent>> subscribers = new CopyOnWriteArrayList<>();

    /** 测试 / 非 Spring 场景：手动 {@link #subscribe} */
    public PostCreatedEventBus() {
    }

    /** Spring 装配：自动收集全部 {@link PostCreatedConsumer} 实现并注册为本总线订阅者 */
    @Autowired
    public PostCreatedEventBus(List<PostCreatedConsumer> subscribers) {
        subscribers.forEach(c -> subscribe(c::consume));
    }

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
