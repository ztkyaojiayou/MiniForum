package com.tkzou.miniforum.recommend.model;

import com.tkzou.miniforum.recommend.TestBehaviors;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ItemCF 构建器单元测试：共现越多的物品相似度越高。
 */
class ItemCfBuilderTest {

    @Test
    void build_shouldRankFrequentlyCooccurredHigher() {
        // 用户1 互动 {1,2,3}，用户2 互动 {1,2,4}，用户3 互动 {1,2}
        List<BehaviorLog> behaviors = List.of(
                TestBehaviors.behavior(1L, 1L, BehaviorType.LIKE),
                TestBehaviors.behavior(1L, 2L, BehaviorType.LIKE),
                TestBehaviors.behavior(1L, 3L, BehaviorType.LIKE),
                TestBehaviors.behavior(2L, 1L, BehaviorType.LIKE),
                TestBehaviors.behavior(2L, 2L, BehaviorType.LIKE),
                TestBehaviors.behavior(2L, 4L, BehaviorType.LIKE),
                TestBehaviors.behavior(3L, 1L, BehaviorType.LIKE),
                TestBehaviors.behavior(3L, 2L, BehaviorType.LIKE));

        ItemCfModel model = new ItemCfBuilder().build(behaviors, 10);

        // sim(1,2)：1、2 被 3 个用户共同互动，应显著高于 sim(1,3)/sim(1,4)
        double sim12 = model.similarity(1L, 2L);
        double sim13 = model.similarity(1L, 3L);
        double sim14 = model.similarity(1L, 4L);
        assertTrue(sim12 > sim13, "sim(1,2)=" + sim12 + " 应大于 sim(1,3)=" + sim13);
        assertTrue(sim12 > sim14, "sim(1,2)=" + sim12 + " 应大于 sim(1,4)=" + sim14);
    }

    @Test
    void build_shouldIgnoreWeakSignals() {
        // 只看深度互动，EXPOSE/VIEW 不参与 ItemCF
        List<BehaviorLog> behaviors = List.of(
                TestBehaviors.behavior(1L, 1L, BehaviorType.EXPOSE),
                TestBehaviors.behavior(1L, 2L, BehaviorType.VIEW),
                TestBehaviors.behavior(1L, 3L, BehaviorType.CLICK));
        ItemCfModel model = new ItemCfBuilder().build(behaviors, 10);
        // 只有 CLICK 是深度互动，单个用户单物品不足以产生共现
        assertTrue(model.size() == 0 || model.similarity(1L, 3L) == 0);
    }
}
