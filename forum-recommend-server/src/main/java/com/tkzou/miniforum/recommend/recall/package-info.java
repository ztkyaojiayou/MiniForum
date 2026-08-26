/**
 * 多路召回（候选集生成）
 * <p>
 * <b>核心职责</b>：从全量内容中筛出候选 postId（几百量级），是推荐漏斗的第②步。
 * 6 路召回并行，每路产出 {@code RecallHit(itemId, score, source)}：
 *
 * <pre>
 * recall/RecallService（总控）→ 并行调各 channel（channel/ 包）：
 *   hot       热门召回（按互动热度分）
 *   topic     话题召回（画像兴趣话题重叠）
 *   category  类目召回（画像兴趣类目重叠）
 *   itemcf    ItemCF 相似召回（历史交互物品的相似物）
 *   newitem   新内容召回（冷启池/新帖流量池）
 *   follow    关注召回（关注的人发的帖 / 关注的转帖，二度关系）
 *     → MergeRecallService：各路 rank 归一化 1/(rank+60) + 通道加权(RecConfig.channelWeight) + 去重
 *     → List&lt;Candidate&gt;（itemId + channelScores + mergeScore）
 * </pre>
 *
 * <b>接口化</b>：{@code RecallChannel} 定义一路召回契约（name/recall），新召回只实现一个 channel 即接入。
 */
package com.tkzou.miniforum.recommend.recall;
