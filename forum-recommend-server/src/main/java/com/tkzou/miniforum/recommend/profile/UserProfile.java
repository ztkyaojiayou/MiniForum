package com.tkzou.miniforum.recommend.profile;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户画像
 * <p>
 * 由 {@link UserProfileAggregator} 从行为日志聚合：话题/类目兴趣权重（归一化）、最近交互序列、活跃度。
 * 微博场景下"话题"是兴趣载体，话题权重优先于类目。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class UserProfile {

    private Long userId;

    /**
     * 话题兴趣权重（归一化，Σ=1），key=话题名。
     * 注意：只聚合内容自动提取的 topics（#话题#），不含 Post.tags 手动标签——tag 不进画像/推荐（见第 02 章）。
     */
    private Map<String, Double> topicWeight = new LinkedHashMap<>();

    /** 类目兴趣权重（归一化，Σ=1），key=类目名 */
    private Map<String, Double> categoryWeight = new LinkedHashMap<>();

    /** 最近交互过的帖子 ID（去重，最近在前） */
    private List<Long> recentItemIds = new ArrayList<>();

    /** 累计行为数 */
    private int behaviorCount;

    /** 活跃度 = log1p(行为数) */
    private double activeLevel;

    public UserProfile() {
    }

    public UserProfile(Long userId, Map<String, Double> topicWeight, Map<String, Double> categoryWeight,
                       List<Long> recentItemIds, int behaviorCount, double activeLevel) {
        this.userId = userId;
        this.topicWeight = new LinkedHashMap<>(topicWeight);
        this.categoryWeight = new LinkedHashMap<>(categoryWeight);
        this.recentItemIds = new ArrayList<>(recentItemIds);
        this.behaviorCount = behaviorCount;
        this.activeLevel = activeLevel;
    }

    /** 是否可视为冷用户（行为过少，需冷启动策略） */
    public boolean isCold(int minBehaviorForWarm) {
        return behaviorCount < minBehaviorForWarm;
    }

    /** 返回用户权重最高的话题 TopK（话题权重非空时），否则返回类目 TopK */
    public List<String> topTopics(int k) {
        List<String> result = new ArrayList<>();
        topicWeight.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(k)
                .forEach(e -> result.add(e.getKey()));
        return result;
    }

    public List<String> topCategories(int k) {
        List<String> result = new ArrayList<>();
        categoryWeight.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(k)
                .forEach(e -> result.add(e.getKey()));
        return result;
    }
}
