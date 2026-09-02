package com.tkzou.miniforum.recommend.mq.consumer;

import com.tkzou.miniforum.feed.FollowFeedStore;
import com.tkzou.miniforum.recommend.mq.PostCreatedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关注流扇出订阅者的拉推分流单元测试
 * <p>
 * 普通作者 → fanout 扇出到粉丝 inbox；大V作者 → writeOutbox 只写自己的 outbox（走拉）。
 */
class FanoutPostCreatedConsumerTest {

    private final FollowFeedStore store = mock(FollowFeedStore.class);
    private final FanoutPostCreatedConsumer consumer = new FanoutPostCreatedConsumer(store);

    private PostCreatedEvent event(Long authorId, Long postId) {
        PostCreatedEvent e = new PostCreatedEvent();
        e.setAuthorId(authorId);
        e.setPostId(postId);
        return e;
    }

    @Test
    void consume_shouldFanoutForNormalAuthor() {
        when(store.isBigV(100L)).thenReturn(false);
        consumer.consume(event(100L, 200L));
        verify(store).fanout(100L, 200L);
        verify(store, never()).writeOutbox(anyLong(), anyLong());
    }

    @Test
    void consume_shouldWriteOutboxForBigV() {
        when(store.isBigV(100L)).thenReturn(true);
        consumer.consume(event(100L, 200L));
        verify(store).writeOutbox(100L, 200L);
        verify(store, never()).fanout(anyLong(), anyLong());
    }
}
