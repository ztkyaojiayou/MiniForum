package com.tkzou.miniforum.recommend.stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 内存帖子创建事件（默认实现，@Profile("!prod")）
 * <p>
 * 本地模式下新帖发现走拉取（NewItemRecall 扫描新内容池 + TrafficPool 曝光时懒初始化），
 * 因此事件在此为占位钩子（记录 debug）。生产由 Kafka 实现异步下发下游。
 */
@Component
@Profile("!prod")
public class InMemoryPostCreatedNotifier implements PostCreatedNotifier {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPostCreatedNotifier.class);

    @Override
    public void notify(PostCreatedEvent event) {
        log.debug("帖子创建事件（内存模式，占位）：postId={}", event.getPostId());
    }
}
