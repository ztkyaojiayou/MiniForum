package com.tkzou.miniforum.recommend.model;

import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ItemCF 模型构建器（弱训练侧，纯 Java）
 * <p>
 * 以"深度互动（转发/评论/收藏/点赞/点击）"构建 用户→物品 隐式反馈矩阵，
 * 计算物品共现相似度 sim(i,j)=co[i][j]/√(N(i)·N(j))（Jaccard 的余弦变体），
 * 每个物品保留 TopK 相似物品。
 */
public class ItemCfBuilder {

    /** 忽略的弱信号（曝光/浏览/搜索/负反馈不构成正反馈） */
    private static boolean isDeepInteraction(BehaviorType type) {
        return type == BehaviorType.REPOST || type == BehaviorType.COMMENT || type == BehaviorType.FAVORITE
                || type == BehaviorType.LIKE || type == BehaviorType.CLICK;
    }

    /**
     * 从行为日志构建 ItemCF 模型
     *
     * @param behaviors 行为日志（作为行为时间线全量）
     * @param topK      每个物品保留的相似物品数
     */
    public ItemCfModel build(List<BehaviorLog> behaviors, int topK) {
        // 用户 → 交互过的物品集合（深度互动）
        Map<Long, Set<Long>> userItems = new HashMap<>();
        for (BehaviorLog b : behaviors) {
            if (b.getUserId() == null || b.getPostId() == null) {
                continue;
            }
            if (!isDeepInteraction(b.getType())) {
                continue;
            }
            userItems.computeIfAbsent(b.getUserId(), k -> new HashSet<>()).add(b.getPostId());
        }

        // 物品共现矩阵 co[a][b] 与 物品的用户数 N(a)
        Map<Long, Map<Long, Integer>> co = new HashMap<>();
        Map<Long, Integer> itemUserCount = new HashMap<>();
        for (Map.Entry<Long, Set<Long>> e : userItems.entrySet()) {
            List<Long> items = new ArrayList<>(e.getValue());
            for (int i = 0; i < items.size(); i++) {
                Long a = items.get(i);
                itemUserCount.merge(a, 1, Integer::sum);
                for (int j = i + 1; j < items.size(); j++) {
                    Long b = items.get(j);
                    co.computeIfAbsent(a, k -> new HashMap<>()).merge(b, 1, Integer::sum);
                    co.computeIfAbsent(b, k -> new HashMap<>()).merge(a, 1, Integer::sum);
                }
            }
        }

        ItemCfModel model = new ItemCfModel();
        for (Map.Entry<Long, Map<Long, Integer>> e : co.entrySet()) {
            Long a = e.getKey();
            int na = itemUserCount.getOrDefault(a, 1);
            List<ItemCfModel.SimilarItem> similarities = e.getValue().entrySet().stream()
                    .map(en -> {
                        Long b = en.getKey();
                        double nb = itemUserCount.getOrDefault(b, 1);
                        double sim = en.getValue() / Math.sqrt((double) na * nb);
                        return new ItemCfModel.SimilarItem(b, sim);
                    })
                    .sorted(Comparator.comparingDouble(ItemCfModel.SimilarItem::similarity).reversed())
                    .limit(topK)
                    .collect(Collectors.toList());
            model.putSimilarities(a, similarities);
        }
        return model;
    }
}
