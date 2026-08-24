package com.tkzou.miniforum.recommend.config;

/**
 * 配置中心接口
 * <p>
 * 抽象"推荐配置动态下发"能力：生产形态由 Nacos 下发（见 prod.nacos.NacosConfigService），
 * 默认使用内存实现（InMemoryConfigService），启动加载 application.yml 的 app.rec.* 默认值，
 * 运行时可热更新（AB 实验/灰度调参）。version() 用于感知配置变更。
 */
public interface ConfigService {

    /** 当前生效配置 */
    RecConfig current();

    /** 更新配置（内存实现直接生效；生产形态发布到配置中心后异步生效） */
    void update(RecConfig config);

    /** 配置版本号（每次更新自增，用于缓存失效） */
    long version();
}
