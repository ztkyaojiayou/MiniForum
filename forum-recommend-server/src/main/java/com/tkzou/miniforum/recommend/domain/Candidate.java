package com.tkzou.miniforum.recommend.domain;
import lombok.Getter;

import java.util.Map;

/**
 * 融合后的候选
 * <p>
 * 多路召回结果经归一化+加权+去重后得到，保留每一路的得分构成（channelScores），
 * 用于排序特征与推荐理由（可解释性）。
 */
// 样板 getter/setter 由 Lombok @Getter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter
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

    @Override
    public String toString() {
        return "Candidate{itemId=" + itemId + ", mergeScore=" + mergeScore + ", channels=" + channelScores.keySet() + '}';
    }
}
