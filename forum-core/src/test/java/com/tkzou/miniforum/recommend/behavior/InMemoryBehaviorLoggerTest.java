package com.tkzou.miniforum.recommend.behavior;
import com.tkzou.miniforum.recommend.behavior.impl.InMemoryBehaviorLogger;

import com.tkzou.miniforum.recommend.mq.BehaviorEventQueue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 异步行为打点测试（P0-5）
 * <p>
 * log() 只入队、后台线程落库+广播（近线语义）；flush() 同步排空保证测试确定性——
 * flush 后行为已写入 {@link BehaviorLogRepository} 并发布到 {@link BehaviorEventQueue}。
 */
class InMemoryBehaviorLoggerTest {

    @Test
    void log_thenFlush_persistsAndPublishes() {
        BehaviorLogRepository repo = new BehaviorLogRepository();
        BehaviorEventQueue queue = new BehaviorEventQueue();
        InMemoryBehaviorLogger logger = new InMemoryBehaviorLogger(repo, queue);

        logger.log(1L, 100L, BehaviorType.EXPOSE, BehaviorScene.RECOMMEND_FEED, "rec-v1");
        logger.log(1L, 101L, BehaviorType.LIKE, BehaviorScene.POST, null);
        logger.flush(); // 同步排空：两条都落库 + 广播

        assertEquals(2, repo.count(), "flush 后行为应全部落库");
        assertEquals(2, queue.size(), "flush 后行为应全部广播到事件队列");
        BehaviorLog first = repo.findAll().get(0);
        assertEquals(BehaviorType.EXPOSE, first.getType());
        assertEquals(1L, first.getUserId());
        assertEquals(100L, first.getPostId());
        assertEquals("rec-v1", first.getExpId());
    }

    @Test
    void log_nullUserId_isIgnored() {
        BehaviorLogRepository repo = new BehaviorLogRepository();
        BehaviorEventQueue queue = new BehaviorEventQueue();
        InMemoryBehaviorLogger logger = new InMemoryBehaviorLogger(repo, queue);

        logger.log(null, 100L, BehaviorType.LIKE, BehaviorScene.POST, null);
        logger.flush();

        assertEquals(0, repo.count(), "userId 为空应直接忽略（不落库不广播）");
    }
}
