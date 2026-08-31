package com.tkzou.miniforum.recommend.coldstart;
import lombok.Getter;
import lombok.Setter;

/**
 * Thompson 后验参数（可序列化值类，替代 NewItemPool 的 double[3]）
 * <p>
 * alpha = 成功（深度互动）次数 + 1、beta = 失败（曝光无转化惩罚）次数 + 1、
 * pendingExposures = 待惩罚曝光数（连续曝光无互动达阈值 3 → beta+1 并清零）。
 * 抽成 public POJO 以便 Jackson 序列化到 Redis。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class AlphaBeta {

    /** 成功次数 + 1（后验 Beta 分布的 alpha 参数） */
    private double alpha = 1.0;
    /** 失败次数 + 1（后验 Beta 分布的 beta 参数） */
    private double beta = 1.0;
    /** 待惩罚曝光数（连续曝光无互动，达阈值后 beta+1） */
    private int pendingExposures;

    public AlphaBeta() {
    }

    public AlphaBeta(double alpha, double beta, int pendingExposures) {
        this.alpha = alpha;
        this.beta = beta;
        this.pendingExposures = pendingExposures;
    }

}
