/**
 * 用户画像域（标准推荐系统 ②用户画像）
 * <p>
 * {@link UserProfile}（话题/类目兴趣权重、最近交互序列、冷启动判定）+ {@link UserProfileAggregator}
 * （行为日志→画像的全量聚合，纯函数）+ {@link UserProfileService}（域入口接口）+ {@link com.tkzou.miniforum.recommend.profile.impl.InMemoryUserProfileService}（默认实现）。
 * <p>
 * 职责边界：只做"用户侧兴趣画像"。物品特征/实时特征见 {@code feature} 包，社交图谱见 {@code graph} 包——
 * 三个域互不依赖，由召回/排序/冷启动等编排层按需注入。
 */
package com.tkzou.miniforum.recommend.profile;
