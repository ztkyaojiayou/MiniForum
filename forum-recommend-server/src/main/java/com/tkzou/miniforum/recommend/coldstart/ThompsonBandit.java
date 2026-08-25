package com.tkzou.miniforum.recommend.coldstart;

/**
 * Thompson Sampling（贝叶斯多臂老虎机）
 * <p>
 * 对每个 arm 维护 Beta(α, β) 后验：点击 α+=1，未点 β+=1。
 * 每次从后验采样一个 θ，θ 大者更可能被选中——天然实现"不确定性加权探索"，无需调探索系数。
 * Gamma 抽样采用 Marsaglia-Tsang 方法。
 */
public class ThompsonBandit {

    private ThompsonBandit() {
    }

    /** 从 Beta(alpha, beta) 采样：X~Gamma(α,1), Y~Gamma(β,1), θ = X/(X+Y) */
    public static double sampleBeta(double alpha, double beta) {
        double x = gammaSample(alpha);
        double y = gammaSample(beta);
        return x / (x + y);
    }

    /** Marsaglia-Tsang 抽样 Gamma(shape, 1)；shape&lt;1 时用 boosting 技巧 */
    public static double gammaSample(double shape) {
        if (shape < 1.0) {
            double u = Math.random();
            return gammaSample(shape + 1.0) * Math.pow(u, 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x = nextGaussian();
            double v = 1.0 + c * x;
            if (v <= 0) {
                continue;
            }
            v = v * v * v;
            double u = Math.random();
            double x2 = x * x;
            if (u < 1.0 - 0.0331 * x2 * x2) {
                return d * v;
            }
            if (Math.log(u) < 0.5 * x2 + d * (1.0 - v + Math.log(v))) {
                return d * v;
            }
        }
    }

    /** Box-Muller 标准正态采样 */
    private static double nextGaussian() {
        double u1 = Math.random();
        double u2 = Math.random();
        if (u1 <= 0) {
            u1 = Double.MIN_VALUE;
        }
        return Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2);
    }
}
