package com.tkzou.miniforum.recommend.profile;

/**
 * 用户画像服务（画像域入口，标准推荐系统 ②用户画像）
 * <p>
 * 把用户行为聚合为长短期兴趣画像（话题/类目权重、最近交互序列），供召回（topic/category/itemcf）、
 * 排序（interest 特征）、冷启动（isCold 判定）消费。
 * 与物品特征 {@link com.tkzou.miniforum.recommend.feature.ItemFeatureService} 解耦——画像域独立演进。
 * 演示实现 {@link InMemoryUserProfileService}；生产经 {@code RedisUserProfileStore} 跨实例共享。
 */
public interface UserProfileService {

    /** 取用户画像（实现内部负责缓存/现算，调用方不关心） */
    UserProfile userProfile(Long userId);
}
