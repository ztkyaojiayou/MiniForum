package com.tkzou.miniforum.recommend.coldstart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
