package com.tkzou.miniforum.recommend.coldstart;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Thompson Bandit 单元测试：采样均值应逼近 α/(α+β)。
 */
class ThompsonBanditTest {

    @Test
    void sampleBeta_mean_shouldApproachExpectation() {
        double alpha = 2, beta = 3;
        int n = 100_000;
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += ThompsonBandit.sampleBeta(alpha, beta);
        }
        double mean = sum / n;
        // E[Beta(2,3)] = 2/5 = 0.4
        assertEquals(0.4, mean, 0.02);
    }

    @Test
    void sampleBeta_shouldBeInUnitInterval() {
        for (int i = 0; i < 1000; i++) {
            double v = ThompsonBandit.sampleBeta(1, 1);
            assertTrue(v >= 0 && v <= 1, "Beta 采样应在 [0,1]，实际=" + v);
        }
    }

    @Test
    void sampleBeta_higherAlpha_shouldTendHigher() {
        double sumHigh = 0, sumLow = 0;
        int n = 20_000;
        for (int i = 0; i < n; i++) {
            sumHigh += ThompsonBandit.sampleBeta(9, 1);
            sumLow += ThompsonBandit.sampleBeta(1, 9);
        }
        assertTrue(sumHigh / n > sumLow / n, "alpha 大的 arm 采样均值应更高");
    }
}
