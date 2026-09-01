package com.tkzou.miniforum.recommend.stream.impl;
import com.tkzou.miniforum.recommend.stream.PostCreatedEventBus;
import com.tkzou.miniforum.recommend.stream.PostCreatedEvent;
import com.tkzou.miniforum.recommend.stream.PostCreatedProducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 内存帖子创建事件（默认实现，@Profile("!prod")）
 * <p>
 * <b>数据流程</b>：发帖落库 → {@link #publish} → {@link PostCreatedEventBus#publish} 广播给全部订阅者
 * （关注流扇出 / 搜索索引 / 内容管道）。生产由 Kafka 实现异步下发，Kafka 消费后同样 publish 到总线。
 * 关注流扇出具体逻辑在 {@code FanoutOnPostCreated} 订阅者。
 */
@Component
@Profile("!prod")
public class InMemoryPostCreatedProducer implements PostCreatedProducer {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPostCreatedProducer.class);

    private final PostCreatedEventBus eventBus;

    public InMemoryPostCreatedProducer(PostCreatedEventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public void publish(PostCreatedEvent event) {
        eventBus.publish(event);
        log.debug("帖子创建事件（内存模式）：postId={} 已广播到事件总线", event.getPostId());
    }
}
