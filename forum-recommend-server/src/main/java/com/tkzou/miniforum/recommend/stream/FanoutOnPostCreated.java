package com.tkzou.miniforum.recommend.stream;

import com.tkzou.miniforum.feed.FollowFeedStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 关注流扇出订阅者（订阅 {@link PostCreatedEventBus}）
 * <p>
 * 收到发帖事件 → {@link FollowFeedStore#fanout} 把 postId 写入作者所有已建流粉丝的 inbox。
 * 与搜索索引等一样是总线的一路消费者：内存发帖（InMemoryPostCreatedNotifier）与生产 Kafka
 * 消费（KafkaPostCreatedConsumer）都 publish 到总线，本订阅者在 @!prod/@prod 通吃。
 * <b>构造器即订阅</b>（Spring 与测试 new 均生效）。
 */
@Component
public class FanoutOnPostCreated {

    private static final Logger log = LoggerFactory.getLogger(FanoutOnPostCreated.class);

    public FanoutOnPostCreated(PostCreatedEventBus eventBus, FollowFeedStore followFeedStore) {
        eventBus.subscribe(event -> {
            followFeedStore.fanout(event.getAuthorId(), event.getPostId());
            log.debug("发帖事件扇出：postId={} → 作者 {} 粉丝关注流 inbox", event.getPostId(), event.getAuthorId());
        });
    }
}
