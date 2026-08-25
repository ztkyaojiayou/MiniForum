package com.tkzou.miniforum.recommend.eval;

/**
 * 推荐质量指标集合（离线评估结果）
 */
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

    public double getAuc() {
        return auc;
    }

    public void setAuc(double auc) {
        this.auc = auc;
    }

    public double getGauc() {
        return gauc;
    }

    public void setGauc(double gauc) {
        this.gauc = gauc;
    }

    public double getRecallAtK() {
        return recallAtK;
    }

    public void setRecallAtK(double recallAtK) {
        this.recallAtK = recallAtK;
    }

    public double getNdcgAtK() {
        return ndcgAtK;
    }

    public void setNdcgAtK(double ndcgAtK) {
        this.ndcgAtK = ndcgAtK;
    }

    public double getCoverage() {
        return coverage;
    }

    public void setCoverage(double coverage) {
        this.coverage = coverage;
    }

    public double getDiversity() {
        return diversity;
    }

    public void setDiversity(double diversity) {
        this.diversity = diversity;
    }

    public double getFreshness() {
        return freshness;
    }

    public void setFreshness(double freshness) {
        this.freshness = freshness;
    }

    public int getEvaluatedUsers() {
        return evaluatedUsers;
    }

    public void setEvaluatedUsers(int evaluatedUsers) {
        this.evaluatedUsers = evaluatedUsers;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }
}
