package com.tkzou.miniforum.feed;

import java.util.List;

/**
 * 关注流 inbox 存储接口
 * <p>
 * 生产级关注流的"推模式"实现：发帖时把 postId 扇出到每个粉丝的 inbox，读时间线 = 读自己的 inbox（O(1)）。
 * 默认使用内存实现（{@link InMemoryFollowFeedStore}，@Profile("!prod")），
 * 生产 profile 使用 Redis ZSet 实现（{@link RedisFollowFeedStore}，@Profile("prod")）。
 * <p>
 * 核心原则：inbox 只存 postId 序列（不存全文），内容按 id 回源；postId 单调递增即天然有序。
 */
public interface FollowFeedStore {

    /** 扇出：作者发帖后，把 postId 写入该作者所有粉丝的 inbox */
    void fanout(Long authorId, Long postId);

    /** 读取用户 inbox 的最新 postId 列表（最新在前，最多 maxCount 条；含封顶） */
    List<Long> getInbox(Long userId, int maxCount);

    /** 关注回填：把一批 postId 补写进 follower 的 inbox（关注新作者/首次建流时用） */
    void onFollow(Long followerId, List<Long> recentPostIds);

    /** 该用户的 inbox 是否已建立（未建立则调用方回退旧查询并触发回填） */
    boolean isBuilt(Long userId);
}
