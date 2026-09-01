package com.tkzou.miniforum.recommend.stream.impl;
import com.tkzou.miniforum.recommend.stream.PostCreatedEvent;
import com.tkzou.miniforum.recommend.stream.PostCreatedNotifier;
import com.tkzou.miniforum.recommend.stream.OutboxStore;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 内存 Outbox（默认实现，@Profile("!prod")）
 * <p>
 * 演示版：enqueue 同步委托 {@link PostCreatedNotifier#notify}（内存实现 → 关注流扇出），
 * 保持"发帖即扇出"的最小等价闭环，零中间件，不引入 outbox 复杂度。
 * 生产由 MySQL 版（MySqlOutboxStore，@Profile("prod")）替代，保证事件必达。
 */
@Component
@Profile("!prod")
public class InMemoryOutboxStore implements OutboxStore {

    private final PostCreatedNotifier postCreatedNotifier;

    public InMemoryOutboxStore(PostCreatedNotifier postCreatedNotifier) {
        this.postCreatedNotifier = postCreatedNotifier;
    }

    @Override
    public void enqueue(PostCreatedEvent event) {
        postCreatedNotifier.notify(event);
    }
}
