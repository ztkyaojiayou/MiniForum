package com.tkzou.miniforum.recommend.rank;

/**
 * 探索加分提供者
 * <p>
 * 排序层调用以获得探索（Exploration）加分，保证新内容/新用户有机会曝光。
 * 默认实现返回 0（DefaultExploreProvider）；冷启动实现由 ColdStartService 提供（Thompson bandit）。
 */
public interface ExploreProvider {

    /** 对 (userId, postId) 的探索加分（如 Thompson 采样分），无可探索性时返回 0 */
    double exploreBonus(Long userId, Long postId);
}
