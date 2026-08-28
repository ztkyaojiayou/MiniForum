/**
 * 推荐系统子系统（业务侧为主，弱训练侧）。
 * <p>
 * 以"feature 式子系统"组织在 layer 式工程内部：核心逻辑全部内聚在本包，
 * 对外仅通过 {@link com.tkzou.miniforum.controller.RecommendController} 暴露 HTTP 接口，
 * 并织入现有 service（点赞/收藏/评论/转发/搜索/关注/浏览）采集统一行为日志。
 *
 * <h3>整体数据流程（漏斗）</h3>
 * <pre>
 * 用户请求 /api/recommend/feed (session:userId)
 *   └→ service.RecommendService         编排：画像→召回→排序→重排→冷启动→下发→曝光日志
 *        ├→ profile.InMemoryUserProfileService   画像(userProfile, 话题/类目兴趣权重)
 *        │    └→ profile.UserProfileAggregator    行为日志 → 话题/类目兴趣权重
 *        ├→ feature.InMemoryItemFeatureService    物品特征(itemFeature)/实时匹配(realtimeMatch)
 *        ├→ graph.InMemorySocialGraphService      社交图谱(followingIds/social/authorFollowers)
 *        ├→ recall.RecallService            6 路召回（hot/topic/category/itemcf/newitem/follow）
 *        │    └→ MergeRecallService         各路 rank 归一化 + 通道加权 + 去重 → Candidate
 *        ├→ rank.RuleRankService            微博式 rankScore（interact/quality/interest/social/author/hot/realtime × 时效）
 *        ├→ rerank.DiversifyRerankService   同类打散(硬约束) + MMR 多样性 → TopN
 *        ├→ coldstart.ColdStartService      Thompson 探索加分 + 新用户热门兜底
 *        └→ behavior.InMemoryBehaviorLogger 下发即记 EXPOSE → 事件队列
 *
 * 行为回流（生产形态 = Kafka → Flink → Redis）：
 *   点赞/收藏/评论/转发/搜索/关注/浏览/点击/负反馈
 *     → behavior.BehaviorLogger → BehaviorLogRepository(JSON 持久化)  ← 画像/评估的事实源
 *     → stream.BehaviorEventQueue(模拟Kafka) → RealtimeFeatureWindow(模拟Flink, 窗口聚合)
 *        → feature.RealtimeFeatureStore(模拟Redis) → 下一次排序特征 realtime
 *
 * 离线（弱训练侧）：
 *   BehaviorLogRepository → model.ItemCfBuilder → ItemCfModelStore（行为数变化自动重建）
 *
 * 配置/实验/评估：
 *   config.RecConfig(配置中心, 内存+Nacos适配) · ab.AbExperimentService(哈希分桶) · eval.OfflineEvaluator(7 指标)
 *
 * 生产适配：prod/kafka·redis·nacos·flink（@Profile("prod") 激活，默认内存实现）
 * </pre>
 */
package com.tkzou.miniforum.recommend;
