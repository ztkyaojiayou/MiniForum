package com.tkzou.miniforum.recommend.rank.impl;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.domain.RecommendScene;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 粗排简化实现单测（架构缺环补齐：召回→粗排→精排）
 * <p>
 * 默认 coarseTopN=200 即透传 + 保序（候选量小）；调低 coarseTopN 则按融合分截断（模拟大厂"千→百"）。
 */
class RuleCoarseRankServiceTest {

    private Candidate cand(long id, double mergeScore) {
        return new Candidate(id, Map.of("hot", mergeScore), mergeScore);
    }

    private RuleCoarseRankService service(RecConfig cfg) {
        ConfigService cs = mock(ConfigService.class);
        when(cs.current()).thenReturn(cfg);
        return new RuleCoarseRankService(cs);
    }

    @Test
    void coarseRank_defaultTopN_keepsAllAndOrdered() {
        RuleCoarseRankService svc = service(RecConfig.defaults()); // coarseTopN=200
        List<Candidate> candidates = List.of(cand(1, 0.1), cand(2, 0.9), cand(3, 0.5));
        List<Candidate> out = svc.coarseRank(
                new RecommendContext(1L, RecommendScene.HOME, LocalDateTime.now(), 10), candidates);
        assertEquals(3, out.size(), "默认 coarseTopN=200 应透传全部候选");
        assertEquals(2L, out.get(0).getItemId(), "按 mergeScore 降序");
        assertEquals(3L, out.get(1).getItemId());
        assertEquals(1L, out.get(2).getItemId());
    }

    @Test
    void coarseRank_lowerTopN_truncates() {
        RecConfig cfg = RecConfig.defaults().copy().coarseTopN(2).build();
        RuleCoarseRankService svc = service(cfg);
        List<Candidate> candidates = List.of(cand(1, 0.1), cand(2, 0.9), cand(3, 0.5));
        List<Candidate> out = svc.coarseRank(
                new RecommendContext(1L, RecommendScene.HOME, LocalDateTime.now(), 10), candidates);
        assertEquals(2, out.size(), "coarseTopN=2 应截断到前 2");
        assertEquals(2L, out.get(0).getItemId());
        assertEquals(3L, out.get(1).getItemId());
    }

    @Test
    void coarseRank_emptyOrNull_returnsEmpty() {
        RuleCoarseRankService svc = service(RecConfig.defaults());
        RecommendContext ctx = new RecommendContext(1L, RecommendScene.HOME, LocalDateTime.now(), 10);
        assertEquals(0, svc.coarseRank(ctx, List.of()).size());
        assertEquals(0, svc.coarseRank(ctx, null).size());
    }
}
