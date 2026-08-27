package com.tkzou.miniforum.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Session 外置（生产适配，@Profile("prod") 激活，需 -Pprod 编译）
 * <p>
 * Spring Session Redis 透明替换 HttpSession：Controller/Interceptor 零改动（仍用 HttpSession API），
 * 登录态（userId/username）存 Redis，多 pod 共享同一 JSESSIONID。
 * 复用项目统一键 {@code app.rec.redis.host/port}；显式 RedisConnectionFactory 覆盖 Boot 自动装配。
 * 演示（!prod）类路径无 spring-session 依赖 → 自动装配不触发 → Tomcat 内存 session，行为不变。
 */
@Configuration
@Profile("prod")
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 604800)
public class SessionConfig {

    @Value("${app.rec.redis.host:localhost}")
    private String redisHost;

    @Value("${app.rec.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        // spring-data-redis 2.x 的 JedisConnectionFactory 无 standalone+pool 双参构造，用单参 + setPoolConfig
        JedisConnectionFactory factory = new JedisConnectionFactory(new RedisStandaloneConfiguration(redisHost, redisPort));
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(50);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(2);
        factory.setPoolConfig(poolConfig);
        return factory;
    }
}
