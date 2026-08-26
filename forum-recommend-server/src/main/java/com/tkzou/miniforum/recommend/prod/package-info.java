/**
 * 生产适配（Kafka / Nacos / Redis）
 * <p>
 * 把推荐链路的内存实现替换为真实中间件（全部 @Profile("prod") 激活，默认内存实现）。
 * <ul>
 *   <li>kafka：KafkaBehaviorLogger（行为→topic "behavior-log"）、KafkaBehaviorConsumer（回灌离线侧）、
 *       KafkaPostCreatedProducer/Consumer（发帖事件 topic "post-created" → 扇出 + 冷启池）；</li>
 *   <li>redis：RedisRealtimeFeatureStore（实时特征，TTL 60s）；</li>
 *   <li>nacos：NacosConfigService（配置中心热刷新）。</li>
 * </ul>
 * 启用：-Pprod 构建 + spring.profiles.active=prod + 配置 app.rec.{kafka,redis,nacos} 地址。
 */
package com.tkzou.miniforum.recommend.prod;
