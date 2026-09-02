package com.tkzou.miniforum.feed;

import java.util.List;
import java.util.Set;

/**
 * 关注流 inbox 存储接口
 * <p>
 * 生产级关注流的"推模式"实现：发帖时把 postId 扇出到每个粉丝的 inbox，读时间线 = 读自己的 inbox（O(1)）。
 * 默认使用内存实现（{@link InMemoryFollowFeedStore}，@Profile("!prod")），
 * 生产 profile 使用 Redis ZSet 实现（{@link com.tkzou.miniforum.feed.impl.RedisFollowFeedStore}，@Profile("prod")）。
 * <p>
 * 核心原则：inbox 只存 postId 序列（不存全文），内容按 id 回源；postId 单调递增即天然有序。
 * <p>
 * <b>大 V 分流（拉推结合）</b>：粉丝数 ≥ 阈值的作者走「拉」——发帖不扇出，只写自己的 outbox
 * （{@link #isBigV} + {@link #writeOutbox}）；读者刷流时实时去其 outbox 拉（{@link #getAuthorTimeline}）合并；
 * 普通作者走「推」（{@link #fanout}）。大 V 集合由 {@link #refreshBigV} 事件驱动维护（关注/取关/删用户后对
 * 受影响作者重数粉丝数），读/扇出两侧都 O(1) 查集合、不逐人 count。详见 docs/关注流拉推结合实施方案.md。
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

    /** 大 V 判定：是否在全局大V集合（粉丝数 ≥ 阈值时的成员）。O(1) 集合查询，不逐人 count。 */
    boolean isBigV(Long authorId);

    /** 全局大V集合快照（供读侧求交：拉组 = bigVIds() ∩ 我的关注；Redis = SMEMBERS feed:bigvs） */
    Set<Long> bigVIds();

    /**
     * 大 V 集合维护（事件驱动，非轮询）：关注/取关/删用户后，对受影响的那一个作者重数粉丝数，
     * 跨过阈值加入全局集合、掉出阈值移除。调用方只在自己改动关系边后调用，O(1)。
     */
    void refreshBigV(Long authorId);

    /**
     * 拉流：读某作者（大 V）自己的时间线（outbox），postId &lt; maxId 的最新 maxCount 条。
     * 内存实现 = postRepository.findByAuthorId 过滤可见 + 截断（outbox 天然在 Post 表里，无需额外结构）；
     * Redis 实现 = ZSet feed:outbox:{authorId} 的 ZREVRANGEBYSCORE（member=postId, score=postId，发帖时 ZADD）。
     */
    List<Long> getAuthorTimeline(Long authorId, Long maxId, int maxCount);

    /** 大 V 发帖：写进自己的 outbox（O(1)）。内存实现 no-op（读时直接查 Post）；Redis 实现 ZADD feed:outbox:{authorId}。 */
    void writeOutbox(Long authorId, Long postId);
}
