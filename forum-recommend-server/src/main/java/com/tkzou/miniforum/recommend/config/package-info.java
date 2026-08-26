/**
 * 推荐配置中心
 * <p>
 * {@link com.tkzou.miniforum.recommend.config.RecConfig}：推荐全部调参（召回/排序权重、冷启比例、
 * 打散参数、时效半衰期、流量池等）的强类型配置对象；{@code ConfigService} 提供读取/热更新。
 * <ul>
 *   <li>InMemoryConfigService（@Profile("!prod")）：从 application.yml 的 app.rec.* 读取，运行时 update() 热更新；</li>
 *   <li>NacosConfigService（@Profile("prod")）：从 Nacos 配置中心拉取 + 监听热刷新。</li>
 * </ul>
 * 双实现体现了"配置可热调、生产走配置中心"的架构（AB 实验变体也基于 RecConfig）。
 */
package com.tkzou.miniforum.recommend.config;
