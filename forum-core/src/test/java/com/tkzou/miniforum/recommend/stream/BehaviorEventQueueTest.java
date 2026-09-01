package com.tkzou.miniforum.recommend.stream;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 行为事件队列有界历史测试（P0-4）
 * <p>
 * history 为有界环形缓冲：超出上限丢弃最旧（防止长跑 OOM），size/history/clear 对外语义不变，
 * history() 返回快照（不受后续清空影响）。
 */
class BehaviorEventQueueTest {

    private static BehaviorLog log(long postId) {
        BehaviorLog b = new BehaviorLog();
        b.setUserId(1L);
        b.setPostId(postId);
        b.setType(BehaviorType.VIEW);
        return b;
    }

    @Test
    void history_shouldBeBounded_droppingOldest() {
        BehaviorEventQueue queue = new BehaviorEventQueue();
        queue.setHistoryCap(5);
        for (int i = 1; i <= 10; i++) {
            queue.publish(log(i));
        }
        assertEquals(5, queue.size(), "超出上限后丢弃最旧，保留最新 5 条");
        assertEquals(6L, queue.history().get(0).getPostId(), "最早保留的是第 6 条");
        assertEquals(10L, queue.history().get(queue.size() - 1).getPostId(), "最新一条仍在");
    }

    @Test
    void history_shouldReturnSnapshot_notLiveView() {
        BehaviorEventQueue queue = new BehaviorEventQueue();
        queue.publish(log(1));
        java.util.List<BehaviorLog> snap = queue.history();
        queue.clearHistory();
        assertEquals(1, snap.size(), "快照不受后续清空影响");
    }

    @Test
    void clearHistory_shouldEmpty() {
        BehaviorEventQueue queue = new BehaviorEventQueue();
        queue.publish(log(1));
        queue.clearHistory();
        assertEquals(0, queue.size());
    }
}
