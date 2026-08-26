/**
 * 重排（打散 + 多样性）
 * <p>
 * <b>核心职责</b>：排序后做多样性约束，避免同话题/同作者扎堆。漏斗第④步。
 *
 * <pre>
 * DiversifyRerankService.rerank(ranked, topN)：
 *   ① 硬约束打散：同话题/同类目连续条数 ≤ categoryMaxCount（默认 2），违规则后移
 *   ② MMR（最大边际相关）：候选按 MMR 公式重排——
 *      mmr = λ·相关分(rankScore) − (1−λ)·与已选集最大相似度（话题/类目重叠）
 *      λ = RecConfig.mmrLambda（默认 0.6），窗口 mmrWindow
 * </pre>
 *
 * 与"关注流同作者打散"（feed 侧）是同一打散思想的两种应用：推荐流走 MMR，
 * 关注流保持时间序 + 渲染层插卡（见 feed 包）。
 */
package com.tkzou.miniforum.recommend.rerank;
