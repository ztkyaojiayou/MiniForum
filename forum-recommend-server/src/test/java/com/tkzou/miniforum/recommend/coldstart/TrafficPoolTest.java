package com.tkzou.miniforum.recommend.coldstart;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.coldstart.impl.InMemoryTrafficPoolStore;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.mq.BehaviorEventQueue;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

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
 * 流量池 Wilson 置信区间下界单元测试
 */
class TrafficPoolTest {

    @Test
    void wilsonLower_isMoreConservativeForSmallSamples() {
        double small = TrafficPool.wilsonLower(0.5, 10, 1.96);
        double large = TrafficPool.wilsonLower(0.5, 1000, 1.96);
        // 小样本置信区间更宽 → 下界更低（防小样本误判）
        assertTrue(small < large, "小样本下界应更低");
        assertTrue(small < 0.5);
        assertTrue(large < 0.5);
    }

    @Test
    void wilsonLower_zeroAndPerfect() {
        assertEquals(0.0, TrafficPool.wilsonLower(0.0, 10, 1.96), 1e-9);
        // 全成功时下界较高（但小样本仍显著低于 1，保留不确定性）
        double perfect = TrafficPool.wilsonLower(1.0, 10, 1.96);
        assertTrue(perfect > 0.6);
        assertTrue(perfect < 1.0);
    }

    @Test
    void wilsonLower_approachesPhatAsSampleGrows() {
        double w = TrafficPool.wilsonLower(0.3, 10000, 1.96);
        assertTrue(Math.abs(w - 0.3) < 0.01, "大样本下界应收敛到互动率");
    }

    @Test
    void wilsonLower_zeroSampleIsZero() {
        assertEquals(0.0, TrafficPool.wilsonLower(0.5, 0, 1.96), 1e-9);
    }

    @Test
    void concurrentExposures_shouldNotLoseCount() throws Exception {
        // P1-16：行为线程并发累计曝光 → 按帖持锁串行化，计数不丢
        ItemFeatureService itemFeatureService = mock(ItemFeatureService.class);
        ItemFeature feature = mock(ItemFeature.class);
        when(feature.isInNewPool()).thenReturn(true);
        when(itemFeatureService.itemFeature(1L)).thenReturn(feature);
        TrafficPoolStore store = new InMemoryTrafficPoolStore();
        TrafficPool pool = new TrafficPool(itemFeatureService, new BehaviorEventQueue(), store);
        ReflectionTestUtils.setField(pool, "enabled", true); // @Value 注入，直构测试默认 false

        int n = 50;
        ExecutorService es = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(es.submit(() -> {
                start.await();
                BehaviorLog b = new BehaviorLog();
                b.setPostId(1L);
                b.setUserId(1L);
                b.setType(BehaviorType.EXPOSE);
                pool.onBehavior(b);
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        es.shutdownNow();

        PostState st = store.get(1L).orElseThrow();
        assertEquals(n, st.getExposures(), "并发曝光累计不得丢失（读-改-写串行化）");
    }
}
