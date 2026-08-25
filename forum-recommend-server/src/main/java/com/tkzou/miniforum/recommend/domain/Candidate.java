package com.tkzou.miniforum.recommend.domain;

import java.util.Map;

/**
 * 融合后的候选
 * <p>
 * 多路召回结果经归一化+加权+去重后得到，保留每一路的得分构成（channelScores），
 * 用于排序特征与推荐理由（可解释性）。
 */
public class Candidate {

    private final Long itemId;

    /** 各路得分，key=通道名，value=归一化后的得分 */
    private final Map<String, Double> channelScores;

    /** 融合分 = Σ(通道权重 × 归一化得分) */
    private final double mergeScore;

    public Candidate(Long itemId, Map<String, Double> channelScores, double mergeScore) {
        this.itemId = itemId;
        this.channelScores = channelScores;
        this.mergeScore = mergeScore;
    }

    public Long getItemId() {
        return itemId;
    }

    public Map<String, Double> getChannelScores() {
        return channelScores;
    }

    public double getMergeScore() {
        return mergeScore;
    }

    @Override
    public String toString() {
        return "Candidate{itemId=" + itemId + ", mergeScore=" + mergeScore + ", channels=" + channelScores.keySet() + '}';
    }
}
