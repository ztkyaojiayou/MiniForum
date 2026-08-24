package com.tkzou.miniforum.recommend.eval;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 离线指标计算器单元测试（AUC / Recall@K / NDCG@K）
 */
class MetricCalculatorTest {

    @Test
    void auc_shouldBeOneForPerfectRanking() {
        List<Double> scores = List.of(0.9, 0.8, 0.2, 0.1);
        List<Integer> labels = List.of(1, 1, 0, 0);
        assertEquals(1.0, MetricCalculator.auc(scores, labels), 1e-6);
    }

    @Test
    void auc_shouldBeZeroForInvertedRanking() {
        // 高分负样本、低分正样本（反向排序）→ AUC 接近 0
        List<Double> scores = List.of(1.0, 2.0, 3.0, 4.0);
        List<Integer> labels = List.of(1, 1, 0, 0);
        assertTrue(MetricCalculator.auc(scores, labels) < 0.5);
    }

    @Test
    void recallAtK_shouldCountHitsInTopK() {
        Set<Long> relevant = Set.of(1L, 5L, 9L);
        List<Long> ranking = List.of(1L, 2L, 3L, 4L, 5L);
        // Top3 命中 1 个，Top5 命中 2 个
        assertEquals(1.0 / 3, MetricCalculator.recallAtK(relevant, ranking, 3), 1e-6);
        assertEquals(2.0 / 3, MetricCalculator.recallAtK(relevant, ranking, 5), 1e-6);
    }

    @Test
    void ndcgAtK_shouldRewardHigherPositions() {
        Set<Long> relevant = Set.of(1L, 5L);
        List<Long> ranking1 = List.of(1L, 5L, 3L); // 相关项在 1、2 位
        List<Long> ranking2 = List.of(3L, 1L, 5L); // 相关项在 2、3 位
        assertTrue(MetricCalculator.ndcgAtK(relevant, ranking1, 3) > MetricCalculator.ndcgAtK(relevant, ranking2, 3));
    }

    @Test
    void gauc_shouldAverageByUserWeight() {
        Map<Long, List<MetricCalculator.LabeledScore>> byUser = new HashMap<>();
        byUser.put(1L, List.of(new MetricCalculator.LabeledScore(0.9, 1), new MetricCalculator.LabeledScore(0.1, 0)));
        byUser.put(2L, List.of(new MetricCalculator.LabeledScore(0.8, 1), new MetricCalculator.LabeledScore(0.2, 0)));
        double gauc = MetricCalculator.gauc(byUser);
        assertTrue(gauc > 0.9, "两个用户各自完美排序，GAUC 应接近 1，实际=" + gauc);
    }
}
