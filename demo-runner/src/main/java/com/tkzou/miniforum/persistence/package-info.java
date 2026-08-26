/**
 * JSON 持久化（演示装配，demo-runner）
 * <p>
 * {@link com.tkzou.miniforum.persistence.DataStore}：启动时从 data/*.json 加载各仓库，
 * 每 30s 定时保存 + 关闭前 @PreDestroy 落盘（loaded 标志防"定时保存早于加载"覆盖空数据）。
 * @Profile("!prod") 演示实现；prod 由 MySqlDataStore（src/prod）替代。
 */
package com.tkzou.miniforum.persistence;
