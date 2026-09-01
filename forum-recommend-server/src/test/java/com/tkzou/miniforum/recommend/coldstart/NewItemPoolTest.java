package com.tkzou.miniforum.recommend.coldstart;
import com.tkzou.miniforum.recommend.coldstart.impl.InMemoryNewItemPoolStore;

import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 新内容冷启动池测试（P2-2 存储外置后）：默认后验分、成功 α+1、连续曝光 β+1、contains。
 */
class NewItemPoolTest {

    private NewItemPool pool;

    @BeforeEach
    void setUp() {
        PostRepository postRepository = mock(PostRepository.class);
        ItemFeatureService itemFeatureService = mock(ItemFeatureService.class);
        when(postRepository.findAll()).thenReturn(List.of()); // poolItems 为空，contains 只依赖 store
        pool = new NewItemPool(postRepository, itemFeatureService, new InMemoryNewItemPoolStore());
    }

    @Test
    void sampleScore_defaultIsInRange() {
        double score = pool.sampleScore(1L);
        assertTrue(score > 0 && score < 1, "默认后验 Beta(1,1) 采样应在 (0,1)，实际 " + score);
    }

    @Test
    void recordOutcome_success_raisesExpect() {
        pool.recordOutcome(1L, true); // α: 1→2
        assertTrue(pool.expect(1L) > 0.5, "成功应提高期望互动率");
    }

    @Test
    void recordOutcome_threeExposures_penalizes() {
        pool.recordOutcome(1L, false);
        pool.recordOutcome(1L, false);
        pool.recordOutcome(1L, false); // 第 3 次曝光无互动 → β: 1→2
        assertTrue(pool.expect(1L) < 0.5, "连续曝光无互动应降低期望互动率");
    }

    @Test
    void contains_afterOutcome() {
        pool.recordOutcome(1L, true);
        assertTrue(pool.contains(1L), "回灌后应在池内（store 已跟踪）");
    }

    @Test
    void concurrentSuccesses_shouldNotLoseCount() throws Exception {
        // P1-16：并发成功回灌（α+1）按 item 持锁串行化，不丢更新
        int n = 30;
        ExecutorService es = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(es.submit(() -> {
                start.await();
                pool.recordOutcome(1L, true);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        es.shutdownNow();

        // α = 1 + 30 = 31，β = 1 → expect = 31/32 ≈ 0.969；丢更新会显著偏低
        assertTrue(pool.expect(1L) > 0.9, "并发成功回灌不得丢更新（α 累加）");
    }
}
