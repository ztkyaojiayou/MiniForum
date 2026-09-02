/**
 * 关注流 inbox 存储（推模式）
 * <p>
 * <b>核心职责</b>：发帖时把 postId 扇出到每个粉丝的 inbox，读关注流 = 读自己的 inbox（O(1)）。
 * 与"拉模式（读时合并关注对象时间线）"相对，属生产级关注流的推模式实现。
 *
 * <h3>数据流转</h3>
 * <pre>
 * 发帖落库 → PostCreatedProducer.publish → FanoutPostCreatedConsumer 分流
 *     ├ 普通作者 → FollowFeedStore.fanout(authorId, postId) → 写入【作者所有已建流粉丝】的 inbox（封顶 feed.cap 淘汰最旧）
 *     └ 大V作者  → FollowFeedStore.writeOutbox(authorId, postId) → 只写自己的 outbox（走拉，粉丝读时拉取）
 *
 * 读关注流 → FollowService.getFollowFeed → getInbox(userId, maxId, maxCount) + 各已关注大V getAuthorTimeline
 *     └→ 读自己 inbox（O(1)）+ 拉大V outbox → 全局 postId 合并去重排序 → 回源帖子 → 过滤（可见 + 作者仍在关注）→ 游标分页
 * </pre>
 *
 * <b>双实现</b>：
 * <ul>
 *   <li>{@link com.tkzou.miniforum.feed.InMemoryFollowFeedStore}（@Profile("!prod")）：
 *       每用户一个 {@code ConcurrentSkipListSet<Long>}，单机演示；</li>
 *   <li>{@link com.tkzou.miniforum.feed.impl.RedisFollowFeedStore}（@Profile("prod)）：
 *       Redis ZSet {@code feed:inbox:{uid}}（member=postId, score=postId）+ 建流标记
 *       {@code feed:built:{uid}}，pipeline 批量扇出，跨线程安全（JedisPool）。</li>
 * </ul>
 *
 * <b>关键设计</b>：
 * <ul>
 *   <li>inbox 只存 postId 序列（不存全文），内容按 id 回源；</li>
 *   <li>fanout 只写给<b>已建流</b>的粉丝——未建流用户首次读取会用完整关注集合回填，避免"半成品流"；</li>
 *   <li>游标分页：postId 单调递增 = 天然时间序，max_id 向下翻历史、since_id 增量刷新；</li>
 *   <li>大V分流（拉推结合）{@link com.tkzou.miniforum.feed.FollowFeedStore#isBigV}：
 *       粉丝超阈值走拉（{@code writeOutbox} 写 outbox + 读侧 {@code getAuthorTimeline} 合并），普通作者走推；
 *       大V集合由 {@code refreshBigV} 事件驱动维护（关注/取关/删用户后重数受影响作者，读/扇出两侧 O(1) 查集合，不逐人 count）。
 *       详见 docs/关注流拉推结合实施方案.md。</li>
 * </ul>
 * 详见 docs/feed流架构调研与对比.md。
 */
package com.tkzou.miniforum.feed;
