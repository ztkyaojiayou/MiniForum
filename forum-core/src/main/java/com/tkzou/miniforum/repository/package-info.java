/**
 * 数据访问层（仓库）
 * <p>
 * <b>核心职责</b>：屏蔽存储细节，向 service 提供内存级 O(1) 读写。默认全部
 * {@code ConcurrentHashMap} 内存实现（@Profile("!prod")），生产由 Redis/MySQL 适配替代。
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li>每个仓库持有自己的 {@code Map<Long, Entity>}，id 由实体 {@code nextId()} 分配（单调）；</li>
 *   <li>{@link com.tkzou.miniforum.repository.PostRepository} 额外维护<b>按作者分桶二级索引</b>
 *       （authorId → SortedSet，createdAt 倒序），使 findByAuthorId 从全表扫描降为 O(K)；
 *       save/deleteById/importAll 三处同步维护索引；</li>
 *   <li>生产关注关系（@Profile("prod")）为 <b>MySQL user_follow 事实表 + Redis following/followers ZSET 缓存</b>
 *       （MySqlFollowRepository，demo-runner/src/prod），写双写、计数走 ZCARD，支撑高频关注流查询；</li>
 *   <li>exportAll/importAll 供持久化（DataStore JSON 快照 / MySqlDataStore）批量落盘与恢复。</li>
 * </ul>
 *
 * <b>共享域定位</b>：admin（业务）与 recommend（推荐）都通过本层访问数据，是跨模块无环依赖的叶节点。
 */
package com.tkzou.miniforum.repository;
