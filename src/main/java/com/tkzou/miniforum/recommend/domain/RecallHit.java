package com.tkzou.miniforum.recommend.domain;

/**
 * 单路召回命中
 * <p>
 * 某一路召回通道输出的一个候选，携带该通道内的原始得分与来源标识（如 hot/topic/itemcf）。
 */
public class RecallHit {

    private final Long itemId;

    /** 该通道内的原始得分（通道内相对可比，跨通道需归一化后再融合） */
    private final double score;

    /** 来源通道名，如 hot/topic/category/itemcf/newitem/follow */
    private final String source;

    public RecallHit(Long itemId, double score, String source) {
        this.itemId = itemId;
        this.score = score;
        this.source = source;
    }

    public Long getItemId() {
        return itemId;
    }

    public double getScore() {
        return score;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "RecallHit{itemId=" + itemId + ", score=" + score + ", source='" + source + "'}";
    }
}
