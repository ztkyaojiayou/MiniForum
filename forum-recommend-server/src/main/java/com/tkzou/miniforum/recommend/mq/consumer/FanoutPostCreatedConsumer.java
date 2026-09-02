package com.tkzou.miniforum.recommend.mq.consumer;
import com.tkzou.miniforum.recommend.mq.consumer.PostCreatedConsumer;
import com.tkzou.miniforum.recommend.mq.PostCreatedEvent;

import com.tkzou.miniforum.feed.FollowFeedStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 关注流扇出订阅者（实现 {@link PostCreatedConsumer}，由 {@link PostCreatedEventBus} 构造器自动注册）
 * <p>
 * 收到发帖事件 → 按作者是否为<b>大V</b>分流：
 * 普通作者 → {@link FollowFeedStore#fanout} 把 postId 写入所有已建流粉丝的 inbox（推）；
 * 大V作者 → {@link FollowFeedStore#writeOutbox} 只写自己的 outbox（拉，粉丝读关注流时实时拉取合并）。
 * 与搜索索引/流量池是总线的一路并行消费者（见 {@link PostCreatedEventBus}）：
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
        if (followFeedStore.isBigV(event.getAuthorId())) {
            // 大V分流：不扇出，只写自己的 outbox（粉丝读关注流时实时拉取合并，见 FollowFeedStore）
            followFeedStore.writeOutbox(event.getAuthorId(), event.getPostId());
            log.debug("大V发帖走拉：作者 {} 新帖写入自己的 outbox（postId={}）", event.getAuthorId(), event.getPostId());
        } else {
            followFeedStore.fanout(event.getAuthorId(), event.getPostId());
            log.debug("发帖事件扇出：postId={} → 作者 {} 粉丝关注流 inbox", event.getPostId(), event.getAuthorId());
        }
    }
}