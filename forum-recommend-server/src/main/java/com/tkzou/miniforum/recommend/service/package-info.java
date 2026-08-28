/**
 * 推荐服务（漏斗编排核心）
 * <p>
 * {@link com.tkzou.miniforum.recommend.service.RecommendService} 是推荐系统的<b>总控</b>，
 * 编排一次完整推荐请求；{@code related} 提供详情页 ItemCF 相似帖。
 *
 * <h3>数据流转（recommend 一次完整漏斗）</h3>
 * <pre>
 * RecommendController.feed(session:userId) → RecommendContext(userId, scene, size)
 *   → RecommendService.recommend
 *     ├① profile.InMemoryUserProfileService.userProfile → 画像（话题/类目兴趣权重）
 *     ├② recall.RecallService.recall → 6 路召回 → MergeRecallService 归一化融合 → List&lt;Candidate&gt;
 *     ├③ rank.RuleRankService.rank → 微博式 rankScore → List&lt;RankedItem&gt;（带特征分构成 + 推荐理由）
 *     ├④ rerank.DiversifyRerankService.rerank → 同类打散 + MMR → TopN
 *     ├⑤ coldstart.ColdStartService → Thompson 探索加分 / 新用户热门兜底
 *     ├⑥ 逐条记 EXPOSE 行为日志（behaviorLogger）
 *     └⑦ PostAssembler.toVO 组装 RecommendPostVO（post + reason + sources + score）下发
 * </pre>
 *
 * <b>架构要点</b>：
 * <ul>
 *   <li>排序/召回<b>接口与实现分离</b>（RecallService/RecallChannel、RankService/RuleRankService、
 *       RerankService/DiversifyRerankService），生产可替换实现；</li>
 *   <li>帖子转 VO 用共享域 {@code PostAssembler}（而非业务 PostService）——<b>消除 recommend→admin 依赖环</b>；</li>
 *   <li>AB 实验 {@code abExperimentService.configFor(expId, uid)} 分桶，行为日志携带 expId 供离线归因。</li>
 * </ul>
 */
package com.tkzou.miniforum.recommend.service;
