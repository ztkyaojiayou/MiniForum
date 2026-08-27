package com.tkzou.miniforum.recommend.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ItemCF 相似度模型
 * <p>
 * itemId → 与该物品最相似的 TopK 物品列表（及其相似度）。
 * 由 {@link ItemCfBuilder} 构建，供召回通道与"相关推荐"使用。
 */
public class ItemCfModel {

    /** 相似物品记录 */
    public record SimilarItem(long itemId, double similarity) {
    }

    private final Map<Long, List<SimilarItem>> simMap = new HashMap<>();

    /** 无参构造（供 Jackson 反序列化 / 空模型） */
    public ItemCfModel() {
    }

    /** 相似表快照（供序列化发布：离线构建 → Redis → 在线加载） */
    public Map<Long, List<SimilarItem>> getSimMap() {
        return simMap;
    }

    /** 从相似表重建模型（反序列化工厂：离线发布的反向操作） */
    public static ItemCfModel from(Map<Long, List<SimilarItem>> map) {
        ItemCfModel model = new ItemCfModel();
        if (map != null) {
            map.forEach(model::putSimilarities);
        }
        return model;
    }

    public void putSimilarities(Long itemId, List<SimilarItem> similarities) {
        simMap.put(itemId, similarities);
    }

    /** 某物品的 TopK 相似物品（无则空列表） */
    public List<SimilarItem> topSimilar(Long itemId, int k) {
        List<SimilarItem> sims = simMap.get(itemId);
        if (sims == null) {
            return List.of();
        }
        return sims.size() <= k ? sims : new ArrayList<>(sims.subList(0, k));
    }

    /** 物品 a 与 b 的相似度（b 不在 a 的 TopK 中则 0） */
    public double similarity(Long a, Long b) {
        List<SimilarItem> sims = simMap.get(a);
        if (sims == null) {
            return 0;
        }
        for (SimilarItem s : sims) {
            if (s.itemId() == b) {
                return s.similarity();
            }
        }
        return 0;
    }

    public boolean hasSimilarity(Long itemId) {
        return simMap.containsKey(itemId);
    }

    public int size() {
        return simMap.size();
    }

    public static ItemCfModel empty() {
        return new ItemCfModel();
    }
}
