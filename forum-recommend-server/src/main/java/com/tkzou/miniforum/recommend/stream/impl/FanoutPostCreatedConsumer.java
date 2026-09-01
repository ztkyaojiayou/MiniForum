package com.tkzou.miniforum.recommend.stream.impl;
import com.tkzou.miniforum.recommend.stream.PostCreatedConsumer;
import com.tkzou.miniforum.recommend.stream.PostCreatedEvent;

import com.tkzou.miniforum.feed.FollowFeedStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 关注流扇出订阅者（实现 {@link PostCreatedConsumer}，由 {@link PostCreatedConsumerRegistrar} 统一注册）
 * <p>
 * 收到发帖事件 → {@link FollowFeedStore#fanout} 把 postId 写入作者所有已建流粉丝的 inbox。
 * 与搜索索引/流量池是总线的一路并行消费者（见 {@link PostCreatedConsumerRegistrar}）：
 * 内存发帖（InMemoryPostCreatedProducer）与生产 Kafka 消费（KafkaPostCreatedConsumer）都
 * publish 到总线，本订阅者在 @!prod/@prod 通吃。
 */
@Component
public class FanoutPostCreatedConsumer implements PostCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(FanoutPostCreatedConsumer.class);

    private final FollowFeedStore followFeedStore;

    public FanoutPostCreatedConsumer(FollowFeedStore followFeedStore) {
        this.followFeedStore = followFeedStore;
    }

    @Override
    public String name() {
        return "follow-fanout";
    }

    @Override
    public void consume(PostCreatedEvent event) {
        followFeedStore.fanout(event.getAuthorId(), event.getPostId());
        log.debug("发帖事件扇出：postId={} → 作者 {} 粉丝关注流 inbox", event.getPostId(), event.getAuthorId());
    }
}