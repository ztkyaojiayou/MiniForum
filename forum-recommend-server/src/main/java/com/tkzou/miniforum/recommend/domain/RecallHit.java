package com.tkzou.miniforum.recommend.domain;
import lombok.Getter;

/**
 * 单路召回命中
 * <p>
 * 某一路召回通道输出的一个候选，携带该通道内的原始得分与来源标识（如 hot/topic/itemcf）。
 */
// 样板 getter/setter 由 Lombok @Getter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter
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

    @Override
    public String toString() {
        return "RecallHit{itemId=" + itemId + ", score=" + score + ", source='" + source + "'}";
    }
}
