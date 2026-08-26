/**
 * 推荐管道中间类型
 * <p>
 * 纯 POJO，承载推荐漏斗各阶段的数据：
 * <ul>
 *   <li>{@code RecommendContext}：请求上下文（userId/scene/requestTime/size）；</li>
 *   <li>{@code Candidate}：召回融合后的候选（itemId + 各路 channelScores + mergeScore）；</li>
 *   <li>{@code RankedItem}：排序后的物品（itemId + rankScore + featureScores + sources + explain 推荐理由）；</li>
 *   <li>{@code RecallHit}：单路召回的命中（itemId + score + source）。</li>
 * </ul>
 */
package com.tkzou.miniforum.recommend.domain;
