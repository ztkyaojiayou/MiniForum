/**
 * 特征域（物品特征 / 实时特征；用户画像见 profile 包）
 * <p>
 * <b>核心职责</b>：为排序/召回提供"内容侧特征"输入，是全漏斗的数据底座之一。
 *
 * <ul>
 *   <li>{@code ItemFeatureService.itemFeature(postId)} → ItemFeature：互动计数、热度分
 *       （3·转发+2·评论+1·赞+1.5·收藏+0.02·浏览+0.05·阅读秒）、时效新鲜度、
 *       inNewPool 标记——<b>与推荐漏斗解耦，可独立调用</b>（关注流/离线评估也用它）；
 *       作者粉丝权重（author 特征）不在此，由 graph.SocialGraphService 提供；</li>
 *   <li>{@code RealtimeFeatureStore}：近线实时特征（用户/物品近 N 分钟互动），
 *       由 stream.RealtimeFeatureWindow（模拟 Flink）写入，排序 realtime 特征读取；
 *       prod 用 RedisRealtimeFeatureStore。</li>
 * </ul>
 *
 * <b>域边界</b>：用户画像见 {@code profile} 包（UserProfileService），社交图谱见 {@code graph} 包
 * （SocialGraphService）——三域互不依赖，由召回/排序/冷启动等编排层按需注入。
 *
 * <b>数据流转</b>：行为日志 → ItemFeature（物品特征，读时现算+短缓存）/ 事件队列
 * → RealtimeFeatureWindow → RealtimeFeatureStore（实时特征）。
 */
package com.tkzou.miniforum.recommend.feature;
