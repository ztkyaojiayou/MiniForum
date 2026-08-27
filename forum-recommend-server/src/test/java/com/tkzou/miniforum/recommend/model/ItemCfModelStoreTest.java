package com.tkzou.miniforum.recommend.model;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.prod.clickhouse.ClickHouseBehaviorStore;
import com.tkzou.miniforum.recommend.prod.redis.ItemCfModelRedisStore;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ItemCF 模型存储测试（P2-1 行为读切换 + P2-3 在线读 Redis）
 * <p>
 * 演示路径（无 Redis）：行为数变化触发本地重建、不变命中缓存；
 * prod-Redis 路径：Redis 命中直接返回（不再 count）、Redis 空 → 从 ClickHouse 重建 + 回写 Redis。
 */
class ItemCfModelStoreTest {

    private BehaviorLog behavior(Long userId, Long postId) {
        BehaviorLog b = new BehaviorLog();
        b.setUserId(userId);
        b.setPostId(postId);
        b.setType(BehaviorType.LIKE);
        return b;
    }

    @Test
    void demoPath_rebuildsOnBehaviorCountChange() {
        BehaviorLogRepository repo = mock(BehaviorLogRepository.class);
        when(repo.count()).thenReturn(2L);
        when(repo.findAll()).thenReturn(List.of(behavior(1L, 1L), behavior(1L, 2L)));
        ItemCfModelStore store = new ItemCfModelStore(repo);

        ItemCfModel m1 = store.get(); // count=2 ≠ -1 → rebuild
        ItemCfModel m2 = store.get(); // count=2 == builtAt → 命中缓存
        assertSame(m1, m2, "行为数不变应命中缓存");

        when(repo.count()).thenReturn(3L); // 行为数变化
        ItemCfModel m3 = store.get();
        assertNotSame(m1, m3, "行为数变化应重建");
    }

    @Test
    void prodPath_redisHit_noCountNoRebuild() {
        BehaviorLogRepository repo = mock(BehaviorLogRepository.class);
        ItemCfModelRedisStore redisStore = mock(ItemCfModelRedisStore.class);
        ClickHouseBehaviorStore chStore = mock(ClickHouseBehaviorStore.class);
        ItemCfModel published = new ItemCfModel();
        published.putSimilarities(1L, List.of(new ItemCfModel.SimilarItem(2L, 0.5)));
        when(redisStore.get()).thenReturn(Optional.of(published));
        ItemCfModelStore store = new ItemCfModelStore(repo);
        ReflectionTestUtils.setField(store, "redisStore", redisStore);
        ReflectionTestUtils.setField(store, "clickHouseBehaviorStore", chStore);

        ItemCfModel m = store.get();
        assertSame(published, m, "Redis 命中应直接返回发布模型（多实例共享）");
        verify(repo, never()).count();
        verify(repo, never()).findAll();
    }

    @Test
    void prodPath_redisEmpty_rebuildsFromClickHouseAndPublishes() {
        BehaviorLogRepository repo = mock(BehaviorLogRepository.class);
        ItemCfModelRedisStore redisStore = mock(ItemCfModelRedisStore.class);
        ClickHouseBehaviorStore chStore = mock(ClickHouseBehaviorStore.class);
        when(redisStore.get()).thenReturn(Optional.empty()); // 离线尚未发布
        when(chStore.findAll()).thenReturn(List.of(behavior(1L, 1L), behavior(1L, 2L)));
        ItemCfModelStore store = new ItemCfModelStore(repo);
        ReflectionTestUtils.setField(store, "redisStore", redisStore);
        ReflectionTestUtils.setField(store, "clickHouseBehaviorStore", chStore);

        store.get();
        verify(chStore).findAll();                            // 生产从 ClickHouse 读全量（P2-1）
        verify(redisStore).publish(any(ItemCfModel.class));   // 重建后回写 Redis（P2-3）
    }
}
