package com.tkzou.miniforum.recommend.eval;
import lombok.Getter;
import lombok.Setter;

/**
 * 推荐质量指标集合（离线评估结果）
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class Metrics {

    private double auc;
    private double gauc;
    private double recallAtK;
    private double ndcgAtK;
    private double coverage;
    private double diversity;
    private double freshness;
    private int evaluatedUsers;
    private int topK;

    public Metrics() {
    }

    /** 输出一行指标文本（用于策略对比表） */
    public String toTableRow() {
        return String.format("%.3f | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f",
                auc, gauc, recallAtK, ndcgAtK, coverage, diversity, freshness);
    }

}
