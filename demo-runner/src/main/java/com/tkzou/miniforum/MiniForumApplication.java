package com.tkzou.miniforum;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 演示启动器（demo-runner 入口）
 * <p>
 * 微服务拆分后，本类是唯一可运行的 Spring Boot 应用：聚合
 * <b>forum-core（共享域）+ forum-admin-server（主业务）+ forum-recommend-server（推荐核心）</b>
 * 为一个<b>单进程、零中间件</b>的演示（内存仓库 + JSON 持久化，行为/事件用进程内事件总线——同步发布-订阅）。
 *
 * <h3>装配与扫描</h3>
 * <ul>
 *   <li>@SpringBootApplication 默认扫 <code>com.tkzou.miniforum</code>——覆盖三模块的全部
 *       @Component/@Service/@Controller/@Repository（含 @Profile("!prod") 内存实现）；</li>
 *   <li>@EnableScheduling 激活全部定时任务：DataStore 持久化、SimulatedActivityService 模拟活动、
 *       TrafficPool 清理、RealtimeFeatureWindow 实时窗口、PostService 回收站清理；</li>
 *   <li>static/ + application.yml 在 demo-runner 的 resources 下，端口 8090。</li>
 * </ul>
 *
 * 生产形态：各服务模块（admin/recommend/offline）将来各自部署（独立 main），
 * 走 Kafka/Redis/MySQL/Nacos 真实中间件（-Pprod + spring.profiles.active=prod），见 docs/生产化落地开发清单.md。
 */
@SpringBootApplication
@EnableScheduling
public class MiniForumApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniForumApplication.class, args);
    }
}
