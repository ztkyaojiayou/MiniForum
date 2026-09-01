package com.tkzou.miniforum.recommend.stream;
import com.tkzou.miniforum.recommend.stream.impl.InMemoryOutboxStore;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 内存 Outbox 单元测试
 * <p>
 * 验证演示实现：enqueue 同步委托 {@link PostCreatedNotifier}（保持"发帖即扇出"闭环）。
 */
class InMemoryOutboxStoreTest {

    /** 记录式 fake PostCreatedNotifier：记录收到的每个事件 */
    private static final class RecordingNotifier implements PostCreatedNotifier {
        final List<PostCreatedEvent> events = new ArrayList<>();

        @Override
        public void notify(PostCreatedEvent event) {
            events.add(event);
        }
    }

    @Test
    void enqueue_shouldDelegateToNotifier() {
        RecordingNotifier notifier = new RecordingNotifier();
        InMemoryOutboxStore store = new InMemoryOutboxStore(notifier);

        PostCreatedEvent event = new PostCreatedEvent(1L, 2L, "alice", "标题", "内容", "科技", List.of("AI"));
        store.enqueue(event);

        assertEquals(1, notifier.events.size());
        assertEquals(1L, notifier.events.get(0).getPostId());
        assertEquals(2L, notifier.events.get(0).getAuthorId());
        assertEquals("AI", notifier.events.get(0).getTopics().get(0));
    }
}
