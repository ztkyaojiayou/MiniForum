/**
 * 统一行为日志（用户行为的"事实源"）
 * <p>
 * <b>核心职责</b>：把用户的每一步行为（浏览/点击/点赞/收藏/评论/转发/搜索/关注/曝光/负反馈/阅读时长）
 * 统一采集为 {@code BehaviorLog}，作为<b>画像聚合、ItemCF 构建、离线评估、实时特征</b>的共同输入。
 * 业务 service（发帖/点赞/评论/收藏/搜索/关注/浏览）只依赖 {@link com.tkzou.miniforum.recommend.behavior.BehaviorLogger}
 * 接口打点，不关心底层实现——这就是"行为全量回流闭环"的落点。
 *
 * <h3>数据流转</h3>
 * <pre>
 * 业务打点 → BehaviorLogger.log(userId, postId, type, ...)
 *     └→ BehaviorLogRepository（内存 + data/behavior-log.json 持久化）  ← 画像/ItemCF/评估事实源
 *     └→ BehaviorEventQueue（模拟 Kafka）→ RealtimeFeatureWindow → RealtimeFeatureStore
 * </pre>
 *
 * <b>双实现</b>：{@link com.tkzou.miniforum.recommend.behavior.InMemoryBehaviorLogger}（@Profile("!prod")）
 * 内存入队；prod 由 prod.kafka.KafkaBehaviorLogger 发 Kafka topic "behavior-log"（独立消费组近线/离线）。
 *
 * 重要：点赞计数存在两套来源——展示用 {@code Post.likeCount}（反范式）与推荐特征用
 * {@code LikeRepository.countByPostId}（本包关联）。评分不要混用。
 */
package com.tkzou.miniforum.recommend.behavior;
