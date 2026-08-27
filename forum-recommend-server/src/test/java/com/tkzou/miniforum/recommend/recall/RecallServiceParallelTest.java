package com.tkzou.miniforum.recommend.recall;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 六路召回并行化测试（P0-4）
 * <p>
 * ① 多路并行：耗时 = 6 路中的最大值而非求和；② 单路失败只丢弃该路（多路召回互为兜底），不拖垮整次召回。
 */
class RecallServiceParallelTest {

    /** 可配置延迟/抛异常的召回通道桩 */
    private static RecallChannel channel(String name, long delayMs, boolean fail) {
        return new RecallChannel() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<RecallHit> recall(RecommendContext ctx, int size) {
                if (fail) {
                    throw new IllegalStateException("channel " + name + " down");
                }
                if (delayMs > 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return List.of(new RecallHit(100L, 1.0, name));
            }
        };
    }

    private static RecallService service(RecallChannel... channels) {
        ConfigService cfg = mock(ConfigService.class);
        when(cfg.current()).thenReturn(RecConfig.defaults());
        return new RecallService(List.of(channels), new MergeRecallService(), cfg);
    }

    @Test
    void recall_runsChannelsInParallel() {
        // 两路各睡 400ms：串行 ≥800ms，并行应 ~400ms（RecallService 固定 6 线程池，与机器核数无关）
        RecallService recall = service(channel("a", 400, false), channel("b", 400, false));
        long start = System.currentTimeMillis();
        List<Candidate> result = recall.recall(new RecommendContext(1L, "HOME", LocalDateTime.now(), 20));
        long elapsed = System.currentTimeMillis() - start;
        assertFalse(result.isEmpty(), "两路并行后应有候选");
        assertTrue(elapsed < 700, "并行应远小于串行的 800ms，实际 " + elapsed + "ms");
    }

    @Test
    void recall_oneChannelFails_othersStillContribute() {
        RecallService recall = service(channel("good", 0, false), channel("bad", 0, true));
        List<Candidate> result = recall.recall(new RecommendContext(1L, "HOME", LocalDateTime.now(), 20));
        assertFalse(result.isEmpty(), "一路失败不应拖垮整次召回（多路召回互为兜底）");
    }
}
