package com.tkzou.miniforum.recommend.eval;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import com.tkzou.miniforum.recommend.prod.clickhouse.ClickHouseBehaviorStore;
import com.tkzou.miniforum.recommend.prod.redis.ItemCfModelRedisStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ItemCF 模型发布器测试（P2-3）
 * <p>
 * prod 路径：从 ClickHouse 构建 → 发布 Redis；演示路径（无 Redis 存储）安全跳过。
 */
class ItemCfModelPublisherTest {

    private BehaviorLog behavior(Long userId, Long postId) {
        BehaviorLog b = new BehaviorLog();
        b.setUserId(userId);
        b.setPostId(postId);
        b.setType(BehaviorType.LIKE);
        return b;
    }

    @Test
    void publish_buildsAndPublishesToRedis() {
        BehaviorLogRepository repo = mock(BehaviorLogRepository.class);
        ClickHouseBehaviorStore chStore = mock(ClickHouseBehaviorStore.class);
        ItemCfModelRedisStore redisStore = mock(ItemCfModelRedisStore.class);
        when(chStore.findAll()).thenReturn(List.of(behavior(1L, 1L), behavior(1L, 2L)));
        ItemCfModelPublisher publisher = new ItemCfModelPublisher(repo);
        ReflectionTestUtils.setField(publisher, "clickHouseBehaviorStore", chStore);
        ReflectionTestUtils.setField(publisher, "itemCfModelRedisStore", redisStore);

        publisher.publish();

        verify(chStore).findAll();                            // 生产从 ClickHouse 读全量
        verify(redisStore).publish(any(ItemCfModel.class));   // 构建后发布 Redis
    }

    @Test
    void publish_skipsWhenNoRedis() {
        BehaviorLogRepository repo = mock(BehaviorLogRepository.class);
        ItemCfModelPublisher publisher = new ItemCfModelPublisher(repo);

        publisher.publish(); // 演示 profile（无 Redis 存储）→ 安全跳过，不抛异常

        verify(repo, never()).findAll();
    }
}
