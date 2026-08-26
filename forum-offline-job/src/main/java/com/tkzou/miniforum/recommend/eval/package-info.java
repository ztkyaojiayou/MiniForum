/**
 * 离线评估（定时任务，forum-offline-job）
 * <p>
 * {@code OfflineEvaluator} 用<b>时间切分</b>（TimeSplitter，前 80% 训练 / 后 20% 测试，防泄漏）
 * 评估推荐质量：构建 ItemCF → 为测试用户生成 TopK → 对比真实深度互动 → 输出
 * AUC / GAUC / Recall@K / NDCG@K / Coverage / Diversity / Freshness 7 指标。
 * {@code OfflineEvalScheduler}（@Scheduled，默认 30min）定时执行，指标写日志 + 追加 data/eval-report.json。
 *
 * <b>独立运行器</b>：OfflineJobApplication 是 forum-offline-job 的 main（扫描 com.tkzou.miniforum，
 * 无 web），生产可单独部署；演示（demo-runner）默认不并入（允许缺位）。
 */
package com.tkzou.miniforum.recommend.eval;
