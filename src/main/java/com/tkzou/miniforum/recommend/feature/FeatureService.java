package com.tkzou.miniforum.recommend.feature;

/**
 * 特征服务接口
 * <p>
 * 在线层获取用户画像、物品特征与实时匹配分的统一入口。
 * 生产形态：画像/物品特征来自 Redis 特征存储，实时匹配分来自近线层；本项目默认内存实现。
 */
public interface FeatureService {

    /** 用户画像（兴趣话题/类目权重、最近交互、活跃度） */
    UserProfile userProfile(Long userId);

    /** 物品（帖子）特征（互动热度、时效、作者权重、冷启标记） */
    ItemFeature itemFeature(Long postId);

    /** 实时匹配分（用户近期兴趣话题 × 帖子话题重叠 + 帖子近期互动爆发），无数据返回 0 */
    double realtimeMatch(Long userId, Long postId);
}
