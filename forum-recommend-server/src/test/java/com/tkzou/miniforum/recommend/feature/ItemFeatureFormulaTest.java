package com.tkzou.miniforum.recommend.feature;

import com.tkzou.miniforum.recommend.config.RecConfig;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 物品特征公式纯单测（无需任何 mock——公式只依赖入参与参数）
 * <p>
 * 覆盖：微博信号权重热度分 / 时效小时数与兜底 / 指数衰减半衰期 / 冷启判定边界。
 * 与 {@link RecConfig#defaults()} 参数（半衰期 4h、新内容 48h、min 互动 5）对齐。
 */
class ItemFeatureFormulaTest {

    private final ItemFeatureFormula formula = new ItemFeatureFormula();

    @Test
    void hotScore_shouldWeightWeiboSignals() {
        // 1转发 1评论 1赞 1收藏 100浏览 60s停留 → 3+2+1+1.5+2+3
        double s = formula.hotScore(1, 1, 1, 1, 100, 60);
        assertEquals(3.0 + 2.0 + 1.0 + 1.5 + 0.02 * 100 + 0.05 * 60, s, 1e-9);
    }

    @Test
    void hotScore_deepInteractionOutweighsMassBrowsing() {
        // 1 转发(3.0) 胜过 100 浏览(0.02×100=2.0)：转发权重是浏览的 150 倍——深互动是真信号，浏览可被刷量
        assertTrue(formula.hotScore(1, 0, 0, 0, 0, 0)
                > formula.hotScore(0, 0, 0, 0, 100, 0));
    }

    @Test
    void ageHours_nullOrFuture_returnsZero() {
        assertEquals(0, formula.ageHours(null, LocalDateTime.now()), 1e-9);
        assertEquals(0, formula.ageHours(LocalDateTime.now().plusHours(2), LocalDateTime.now()), 1e-9);
    }

    @Test
    void ageHours_90Minutes_isOnePointFiveHours() {
        LocalDateTime now = LocalDateTime.now();
        assertEquals(1.5, formula.ageHours(now.minusMinutes(90), now), 1e-9);
    }

    @Test
    void freshness_atHalfLife_isHalf() {
        // 半衰期 4h：发布 4h 后新鲜度正好 0.5
        assertEquals(0.5, formula.freshness(4.0, 4.0), 1e-9);
        assertEquals(0.5, formula.freshness(48.0, 48.0), 1e-9);
    }

    @Test
    void freshness_justPublished_isOne_andDecays() {
        assertEquals(1.0, formula.freshness(0.0, 4.0), 1e-9);
        assertTrue(formula.freshness(1.0, 4.0) > formula.freshness(8.0, 4.0)); // 越老越不"新鲜"
    }

    @Test
    void inNewPool_youngOrLowInteraction_returnsTrue() {
        // 发布 2h（< 48h）→ 新内容
        assertTrue(formula.inNewPool(2.0, 100, 48.0, 5));
        // 发布 100h（> 48h）但互动仅 1（< 5）→ 互动过少也算冷启
        assertTrue(formula.inNewPool(100.0, 1, 48.0, 5));
    }

    @Test
    void inNewPool_oldEnoughWithInteractions_returnsFalse() {
        // 发布 100h（> 48h）且互动 50（> 5）→ 已是成熟内容，不进冷启池
        assertFalse(formula.inNewPool(100.0, 50, 48.0, 5));
    }

    @Test
    void defaultsConfig_wireUp() {
        // 与 RecConfig 默认参数打通：hotScore 全 0 时仍非冷启？否——互动 0 < min 5 → 冷启
        RecConfig cfg = RecConfig.defaults();
        assertTrue(formula.inNewPool(100.0, 0,
                cfg.getNewItemAgeHours(), cfg.getNewItemMinInteractions()));
        assertEquals(1.0, formula.freshness(0, cfg.getHalfLifeHours()), 1e-9);
    }
}
