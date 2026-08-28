/**
 * 微博式排序（精排，规则加权）
 * <p>
 * <b>核心职责</b>：对召回候选打分排序，产出带特征分构成与推荐理由的 {@code RankedItem}。漏斗第③步。
 *
 * <h3>rankScore 公式（RuleRankService）</h3>
 * <pre>
 * rankScore = ( Σ 特征权重×特征值 + 探索加分 + 流量池加分 ) × 时效新鲜度
 * 特征（featureScores）：
 *   interact  互动热度 log1p(hotScore)     quality 互动率(深度互动/曝光)
 *   interest  兴趣匹配(0.6×话题重叠+0.4×ItemCF)  social 关注关系(作者=我关注 +1, 关注的人转发过 +0.5)
 *   author    作者粉丝权重 log1p           hot  候选集内热点归一化
 *   realtime  实时特征匹配（近线窗口聚合）
 * 时效新鲜度 = exp(-ln2·ageHours/halfLife)（RecConfig.halfLifeHours，默认 4h）
 * </pre>
 *
 * 特征统一取自三域：{@code ItemFeatureService.itemFeature(postId)}（物品特征）、
 * {@code UserProfileService.userProfile(uid)}（画像 interest）、{@code SocialGraphService}（social/author 社交特征）——与推荐漏斗解耦，可独立调用。
 * 权重来自 {@code RecConfig.rankWeight}（运行时可热更新 / Nacos）。
 */
package com.tkzou.miniforum.recommend.rank;
