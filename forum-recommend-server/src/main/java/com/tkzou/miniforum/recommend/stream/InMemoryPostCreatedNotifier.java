package com.tkzou.miniforum.recommend.stream;

import com.tkzou.miniforum.feed.FollowFeedStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 内存帖子创建事件（默认实现，@Profile("!prod")）
 * <p>
 * <b>数据流程</b>：发帖落库 → {@link #notify} → {@link FollowFeedStore#fanout} 把 postId 扇出到
 * 作者所有粉丝的 inbox（关注流推模式的演示级落地）。生产由 Kafka 实现异步下发下游。
 */
@Component
@Profile("!prod")
public class InMemoryPostCreatedNotifier implements PostCreatedNotifier {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPostCreatedNotifier.class);

    private final FollowFeedStore followFeedStore;

    public InMemoryPostCreatedNotifier(FollowFeedStore followFeedStore) {
        this.followFeedStore = followFeedStore;
    }

    @Override
    public void notify(PostCreatedEvent event) {
        // 扇出：新帖写入作者所有粉丝的关注流 inbox
        followFeedStore.fanout(event.getAuthorId(), event.getPostId());
        log.debug("帖子创建事件（内存模式）：postId={} 已扇出到关注者 inbox", event.getPostId());
    }
}
