package com.tkzou.miniforum.recommend.feature;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 物品特征公式（纯算法，无任何依赖，可独立单测）
 * <p>
 * 从 {@code InMemoryItemFeatureService} 抽出：微博信号权重热度分、时效衰减、冷启判定等<b>领域公式</b>与
 * "取数 / 缓存"解耦——算法只依赖入参与参数，换存储/缓存形态不影响。权重抽成<b>命名常量</b>（魔法值治理），
 * 将来可下沉 {@code RecConfig} 变成可调策略（AB 实验变体用）。
 */
public class ItemFeatureFormula {

    /** 微博信号权重：转发 &gt; 评论 &gt; 赞 &gt; 收藏（互动越深权重越高） */
    public static final double WEIGHT_REPOST = 3.0;
    public static final double WEIGHT_COMMENT = 2.0;
    public static final double WEIGHT_LIKE = 1.0;
    public static final double WEIGHT_FAVORITE = 1.5;
    /** 浏览权重极低（防"标题党刷量上榜"） */
    public static final double WEIGHT_VIEW = 0.02;
    /** 阅读时长也计入热度（仿抖音观看时长——真看完才算数） */
    public static final double WEIGHT_READ_SEC = 0.05;

    /**
     * 互动热度分（微博信号加权求和，喂热门/排序）：
     * <pre>hotScore = 3·转发 + 2·评论 + 1·赞 + 1.5·收藏 + 0.02·浏览 + 0.05·阅读时长</pre>
     */
    public double hotScore(long repost, long comment, long like, long favorite, long view, double readSec) {
        return WEIGHT_REPOST * repost + WEIGHT_COMMENT * comment + WEIGHT_LIKE * like
                + WEIGHT_FAVORITE * favorite + WEIGHT_VIEW * view + WEIGHT_READ_SEC * readSec;
    }

    /** 发布时间距今小时数（createdAt 为空或未来 → 兜底 0，不产生负时效） */
    public double ageHours(LocalDateTime createdAt, LocalDateTime now) {
        if (createdAt == null) {
            return 0;
        }
        return Math.max(0, Duration.between(createdAt, now).toMinutes() / 60.0);
    }

    /** 时效新鲜度：指数衰减 exp(-ln2·ageHours/halfLife)——半衰期即新鲜度掉到 1/2 所需小时，越新越"新鲜" */
    public double freshness(double ageHours, double halfLifeHours) {
        return Math.exp(-Math.log(2) * ageHours / halfLifeHours);
    }

    /** 冷启内容判定：新发布（ageHours 小时内）或互动过少（低于 minInteractions）→ 进冷启池保底曝光 */
    public boolean inNewPool(double ageHours, long interactions, double newItemAgeHours, long minInteractions) {
        return ageHours < newItemAgeHours || interactions < minInteractions;
    }
}
