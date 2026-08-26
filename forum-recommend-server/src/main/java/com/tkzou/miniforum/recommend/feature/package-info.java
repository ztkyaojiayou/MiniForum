/**
 * 特征（用户画像 / 物品特征 / 实时特征）
 * <p>
 * <b>核心职责</b>：为排序/召回提供特征输入，是全漏斗的数据底座。
 *
 * <ul>
 *   <li>{@code FeatureService.itemFeature(postId)} → ItemFeature：互动计数、热度分
 *       （3·转发+2·评论+1·赞+1.5·收藏+0.02·浏览+0.05·阅读秒）、时效新鲜度、作者粉丝权重、
 *       inNewPool 标记——<b>与推荐漏斗解耦，可独立调用</b>（关注流/离线评估也用它）；</li>
 *   <li>{@code FeatureService.userProfile(uid)} → UserProfile：UserProfileAggregator 把行为日志
 *       按话题/类目聚合兴趣权重（带时间衰减 0.7/天）+ 最近交互序列 + 活跃度；</li>
 *   <li>{@code RealtimeFeatureStore}：近线实时特征（用户/物品近 N 分钟互动），
 *       由 stream.RealtimeFeatureWindow（模拟 Flink）写入，排序 realtime 特征读取；
 *       prod 用 RedisRealtimeFeatureStore。</li>
 * </ul>
 *
 * <b>数据流转</b>：行为日志 → UserProfileAggregator（画像）/ ItemFeature（物品特征）/
 * 事件队列 → RealtimeFeatureWindow → RealtimeFeatureStore（实时特征）。
 */
package com.tkzou.miniforum.recommend.feature;
