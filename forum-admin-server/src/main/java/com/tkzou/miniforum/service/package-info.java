/**
 * 业务服务层（主业务，admin）
 * <p>
 * 帖子/用户/评论/关注/搜索/热搜/通知/私信等核心业务的 Service，位于 forum-admin-server，
 * 依赖共享域 forum-core（实体/仓库/行为接口/PostAssembler），<b>不依赖 recommend-server</b>。
 *
 * <b>关键数据流转</b>：
 * <pre>
 * 发帖：PostService.createPost → 规范化/话题提取/@提及 → save → postCreatedNotifier.notify（事件，扇出/冷启）
 * 关注：FollowService.follow → 存关系 + 关注流回填(onFollow)
 * 互动：like/comment/favorite/repost → 更新计数 + behaviorLogger.log（行为回流）
 * </pre>
 *
 * <b>架构要点</b>：
 * <ul>
 *   <li>行为打点只依赖 {@code BehaviorLogger} 接口（forum-core），实现由 demo 装配注入；</li>
 *   <li>发帖事件只依赖 {@code PostCreatedNotifier} 接口（forum-core），实现（内存/Kafka）在 recommend 侧；</li>
 *   <li>帖子转 VO 用共享域 {@code PostAssembler}（与 recommend 共用，破依赖环）；</li>
 *   <li>关注流读取用推模式 inbox（feed.FollowFeedStore）。</li>
 * </ul>
 */
package com.tkzou.miniforum.service;
