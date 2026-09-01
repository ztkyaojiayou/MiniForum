/**
 * 关注流 inbox 存储（推模式）
 * <p>
 * <b>核心职责</b>：发帖时把 postId 扇出到每个粉丝的 inbox，读关注流 = 读自己的 inbox（O(1)）。
 * 与"拉模式（读时合并关注对象时间线）"相对，属生产级关注流的推模式实现。
 *
 * <h3>数据流转</h3>
 * <pre>
 * 发帖落库 → PostCreatedNotifier.notify → FollowFeedStore.fanout(authorId, postId)
 *     └→ 写入【作者所有已建流粉丝】的 inbox（postId 升序 = 时间序，封顶 feed.cap 淘汰最旧）
 *
 * 读关注流 → FollowService.getFollowFeed → getInbox(userId, maxId, maxCount)
 *     └→ 读自己 inbox（O(1)）→ 按 postId 回源帖子 → 过滤（可见 + 作者仍在关注）→ 游标分页
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
 *   <li>大V分流预留 {@link com.tkzou.miniforum.feed.FollowFeedStore#shouldSkipFanout}：
 *       粉丝超阈值跳过扇出（走拉），激活前必须先实现读侧 pull 合并。</li>
 * </ul>
 * 详见 docs/feed流架构调研与对比.md。
 */
package com.tkzou.miniforum.feed;
