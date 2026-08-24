package com.tkzou.miniforum.recommend.model;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * ItemCF 打分器
 * <p>
 * 对候选物品：与其在用户历史交互物品上的相似度求和，作为个性化匹配分。
 */
@Component
public class ItemCfScorer {

    /**
     * 计算用户对候选物品的 ItemCF 匹配分
     *
     * @param candidateItemId  候选物品
     * @param model            ItemCF 相似度模型
     * @param userHistoryItems 用户历史交互过的物品
     */
    public double score(Long candidateItemId, ItemCfModel model, List<Long> userHistoryItems) {
        double score = 0;
        for (Long h : userHistoryItems) {
            score += model.similarity(candidateItemId, h);
        }
        return score;
    }
}
