package com.tkzou.miniforum.recommend.mq.impl;
import com.tkzou.miniforum.recommend.mq.PostCreatedEvent;
import com.tkzou.miniforum.recommend.mq.producer.PostCreatedProducer;
import com.tkzou.miniforum.recommend.mq.OutboxStore;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 内存 Outbox（默认实现，@Profile("!prod")）
 * <p>
 * 演示版：enqueue 同步委托 {@link PostCreatedProducer#publish}（内存实现 → 关注流扇出），
 * 保持"发帖即扇出"的最小等价闭环，零中间件，不引入 outbox 复杂度。
 * 生产由 MySQL 版（MySqlOutboxStore，@Profile("prod")）替代，保证事件必达。
 */
@Component
@Profile("!prod")
public class InMemoryOutboxStore implements OutboxStore {

    private final PostCreatedProducer postCreatedProducer;

    public InMemoryOutboxStore(PostCreatedProducer postCreatedProducer) {
        this.postCreatedProducer = postCreatedProducer;
    }

    @Override
    public void enqueue(PostCreatedEvent event) {
        postCreatedProducer.publish(event);
    }
}
