package com.tkzou.miniforum.recommend.domain;

import java.util.List;
import java.util.Map;

/**
 * 排序后的条目
 * <p>
 * 精排输出：携带最终排序分、各特征分构成（用于可解释推荐理由）与命中的召回路来源。
 */
public class RankedItem {

    private final Long itemId;

    /** 最终排序分（微博式 rankScore） */
    private final double rankScore;

    /** 特征分构成，key=特征名（interact/quality/interest/social/author/hot/realtime），value=归一化分 */
    private final Map<String, Double> featureScores;

    /** 命中的召回路来源（hot/topic/itemcf...） */
    private final List<String> sources;

    /** 可读的推荐理由，如 "因为你看过 #科技#" */
    private final String explain;

    public RankedItem(Long itemId, double rankScore, Map<String, Double> featureScores,
                      List<String> sources, String explain) {
        this.itemId = itemId;
        this.rankScore = rankScore;
        this.featureScores = featureScores;
        this.sources = sources;
        this.explain = explain;
    }

    public Long getItemId() {
        return itemId;
    }

    public double getRankScore() {
        return rankScore;
    }

    public Map<String, Double> getFeatureScores() {
        return featureScores;
    }

    public List<String> getSources() {
        return sources;
    }

    public String getExplain() {
        return explain;
    }

    @Override
    public String toString() {
        return "RankedItem{itemId=" + itemId + ", rankScore=" + rankScore + ", explain='" + explain + "'}";
    }
}
