/**
 * AB 实验分桶
 * <p>
 * {@link com.tkzou.miniforum.recommend.ab.AbExperimentService}：按用户哈希分层分桶，
 * 为每个请求选择实验组配置（对照组 A 全量配置 / 实验组 B 多样性变体），
 * 推荐行为日志携带 expId → 离线评估按桶归因。
 *
 * 实现：floorMod(hash(uid:salt), 100) 分桶；configFor(expId, uid) 返回该桶的 RecConfig。
 */
package com.tkzou.miniforum.recommend.ab;
