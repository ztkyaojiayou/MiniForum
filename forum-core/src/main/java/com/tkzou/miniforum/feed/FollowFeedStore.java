package com.tkzou.miniforum.feed;

import java.util.List;

/**
 * 关注流 inbox 存储接口
 * <p>
 * 生产级关注流的"推模式"实现：发帖时把 postId 扇出到每个粉丝的 inbox，读时间线 = 读自己的 inbox（O(1)）。
 * 默认使用内存实现（{@link InMemoryFollowFeedStore}，@Profile("!prod")），
 * 生产 profile 使用 Redis ZSet 实现（{@link com.tkzou.miniforum.feed.impl.RedisFollowFeedStore}，@Profile("prod")）。
 * <p>
 * 核心原则：inbox 只存 postId 序列（不存全文），内容按 id 回源；postId 单调递增即天然有序。
 */
public interface FollowFeedStore {

    /** 扇出：作者发帖后，把 postId 写入该作者所有粉丝的 inbox */
    void fanout(Long authorId, Long postId);

    /**
     * 向下游标读取：用户 inbox 的最新 postId 列表（最新在前，postId 严格 &lt; maxId，
     * 最多 maxCount 条；maxId 为 null 表示从最新开始）。
     * postId 单调递增即天然时间序，游标边界与展示顺序一致。
     */
    List<Long> getInbox(Long userId, Long maxId, int maxCount);

    /** 增量读取：最新在前，postId 严格 &gt; sinceId（用于 since 增量刷新 / 新帖提示） */
    List<Long> getInboxAfter(Long userId, Long sinceId, int maxCount);

    /** 关注回填：把一批 postId 补写进 follower 的 inbox（关注新作者/首次建流时用） */
    void onFollow(Long followerId, List<Long> recentPostIds);

    /** 该用户的 inbox 是否已建立（未建立则调用方回退旧查询并触发回填） */
    boolean isBuilt(Long userId);

    /**
     * 大V分流预留：该作者粉丝数超过阈值时跳过扇出（走拉模式）。
     * ⚠ 激活前必须先实现读侧 pull 合并（outbox + 读者拉取，见 docs §5/§2.5），否则大V新帖不会进粉丝 inbox。
     */
    boolean shouldSkipFanout(Long userId);
}
