/**
 * 物品相似度模型（ItemCF，弱训练侧）
 * <p>
 * <b>核心职责</b>：从行为日志构建"物品-物品"共现相似度表，供 ItemCF 召回、兴趣特征、详情"相关推荐"。
 *
 * <h3>数据流转</h3>
 * <pre>
 * BehaviorLogRepository（行为事实源）
 *   → ItemCfBuilder.build(behaviors, topK)  共现余弦相似度 + 弱信号过滤
 *   → ItemCfModelStore（@Component）       行为数变化时自动 rebuild()，缓存模型
 *   → ItemCfModel.topSimilar(postId, k)    相似物品 TopK
 * </pre>
 *
 * 使用方：recall.ItemCfRecall（召回）、rank.RuleFineRankService（interest 特征）、
 * recommend.service.RecommendService.related（详情相关推荐）。
 * 属"离线构建 + 在线读取"一体件：构建在行为数变化时触发（演示），生产可由 offline-job 离线构建后发布。
 */
package com.tkzou.miniforum.recommend.model;
