package com.tkzou.miniforum.recommend.recall;

import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;

import java.util.List;

/**
 * 单路召回通道接口
 * <p>
 * 每路召回从一个角度从全量帖子池中捞出候选（热门/话题/类目/ItemCF/新内容/关注）。
 * 得分在通道内相对可比，跨通道由 {@link MergeRecallService} 做 rank 归一化后融合。
 */
public interface RecallChannel {

    /** 通道名（用于融合权重与推荐理由），如 hot/topic/category/itemcf/newitem/follow */
    String name();

    /**
     * 召回候选
     *
     * @param ctx  请求上下文（含 userId/scene）
     * @param size 本通道最多返回条数
     */
    List<RecallHit> recall(RecommendContext ctx, int size);
}
