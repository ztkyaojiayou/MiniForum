/**
 * 冷启动（新内容探索 + 新用户兜底）
 * <p>
 * <b>核心职责</b>：解决"新帖没人见过→没人互动→更没人见"的冷启动循环，漏斗第⑤步。
 *
 * <ul>
 *   <li><b>新内容流量池/赛马</b>（TrafficPool）：新帖渐进式曝光档位（50→500→5000→50000），
 *       用 <b>Wilson 置信区间下界</b>判断互动率显著高于基线 → 晋级加曝光；低于下界 → 停止探索。
 *       监听行为事件（{@code onBehavior}）+ 发帖事件（{@code notifyCreated}）。</li>
 *   <li><b>Thompson Bandit</b>（ThompsonBandit）：对新内容维护 Beta(α,β) 后验，
 *       finalScore = 利用分×(1−λ) + 采样分×λ，λ 随行为量衰减（新用户高探索、老用户低）。</li>
 *   <li><b>新用户兜底</b>（ColdStartService）：冷用户无画像时补热门帖，避免空流。</li>
 *   <li>NewItemPool：冷启新内容池，行为回流经 ColdStartFeedbackListener 更新后验。</li>
 * </ul>
 *
 * <b>数据流转</b>：行为/曝光 → TrafficPool.onBehavior → Wilson 判晋级；曝光后
 * ColdStartFeedbackListener → NewItemPool.recordOutcome（点击 α+1 / 曝光无转化 β+1）。
 */
package com.tkzou.miniforum.recommend.coldstart;
