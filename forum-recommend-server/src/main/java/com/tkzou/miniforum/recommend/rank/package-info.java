/**
 * 排序服务（粗排 / 精排 / 重排）
 * <p>
 * <b>顶层</b>：{@code RankService} 是"排序"的统一抽象，三个串行阶段继承它：
 * <ul>
 *   <li>{@code CoarseRankService} 粗排（漏斗第②.5步）：把融合候选从"千"缩到"百"，控精排算力——实现 {@code RuleCoarseRankService}（按融合分截断，默认透传）；</li>
 *   <li>{@code FineRankService} 精排（漏斗第③步）：对缩后候选逐条打分排序——实现 {@code RuleFineRankService}（微博式 rankScore）；</li>
 *   <li>{@code RerankService} 重排（漏斗第④步）：对精排结果做 list-wise 打散 + 多样性——实现 {@code DiversifyRerankService}（MMR）。</li>
 * </ul>
 *
 * <h3>rankScore 公式（RuleFineRankService）</h3>
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
 * <h3>重排（DiversifyRerankService，原 rerank 包并入本包）</h3>
 * <pre>
 * rerank(ranked, topN)：
 *   ① 硬约束打散：同话题/同类目连续条数 ≤ categoryMaxCount（默认 2），违规则后移
 *   ② MMR（最大边际相关）：mmr = λ·相关分(rankScore) − (1−λ)·与已选集最大相似度（话题/类目重叠）
 *      λ = RecConfig.mmrLambda（默认 0.6），窗口 mmrWindow
 * </pre>
 *
 * 特征统一取自三域：{@code ItemFeatureService.itemFeature(postId)}（物品特征）、
 * {@code UserProfileService.userProfile(uid)}（画像 interest）、{@code SocialGraphService}（social/author 社交特征）——与推荐漏斗解耦，可独立调用。
 * 权重来自 {@code RecConfig.rankWeight}（运行时可热更新 / Nacos）。
 * 与"关注流同作者打散"（feed 侧）是同一打散思想的两种应用：推荐流走 MMR，关注流保持时间序 + 渲染层插卡。
 */
package com.tkzou.miniforum.recommend.rank;
