/**
 * 社交图谱域（标准推荐系统 ④图关系）
 * <p>
 * {@link SocialGraphService}（域入口接口）+ {@link com.tkzou.miniforum.recommend.graph.impl.InMemorySocialGraphService}（默认实现，委托 core 的
 * {@code FollowRepository}/{@code PostRepository}）。
 * <p>
 * 职责边界：只做"关注/粉丝/二度关系"的社交信号查询（followingIds / isFollowing / followedRepostedIds / authorFollowers）。
 * 关注流 inbox（FollowFeedStore）与关注业务（admin 的 FollowService）不属本域（feed 扇出/UI），仍在 core/admin；
 * 画像见 {@code profile} 包，物品特征见 {@code feature} 包——三域互不依赖。
 */
package com.tkzou.miniforum.recommend.graph;
