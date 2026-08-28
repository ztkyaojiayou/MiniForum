package com.tkzou.miniforum.recommend.feature;

/**
 * 物品特征服务（特征域入口，标准推荐系统 ⑥特征）
 * <p>
 * 提供物品（帖子）特征与实时匹配分：
 * <ul>
 *   <li>{@link #itemFeature}：互动热度/时效/冷启标记等"内容侧特征"（召回/排序/重排/冷启动共用）；</li>
 *   <li>{@link #realtimeMatch}：用户近期兴趣话题 × 帖子话题重叠 + 帖子近期互动爆发（近线实时特征）。</li>
 * </ul>
 * 与画像域（profile）、社交图谱域（graph）解耦——作者粉丝数等社交特征由 {@code graph.SocialGraphService} 提供。
 * 演示实现 {@link InMemoryItemFeatureService}；生产实时特征经 {@code RedisRealtimeFeatureStore}。
 */
public interface ItemFeatureService {

    /** 物品（帖子）特征（互动热度、时效、冷启标记） */
    ItemFeature itemFeature(Long postId);

    /** 实时匹配分（用户近期兴趣话题 × 帖子话题重叠 + 帖子近期互动爆发），无数据返回 0 */
    double realtimeMatch(Long userId, Long postId);
}
