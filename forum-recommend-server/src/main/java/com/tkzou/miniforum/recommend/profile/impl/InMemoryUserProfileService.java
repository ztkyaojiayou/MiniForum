package com.tkzou.miniforum.recommend.profile.impl;
import com.tkzou.miniforum.recommend.profile.UserProfileAggregator;
import com.tkzou.miniforum.recommend.profile.UserProfile;
import com.tkzou.miniforum.recommend.profile.UserProfileService;

import com.tkzou.miniforum.recommend.prod.redis.RedisUserProfileStore;
import com.tkzou.miniforum.util.TtlCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 画像服务内存默认实现（无 profile 组件，演示/生产通吃）
 * <p>
 * <b>数据流程</b>：{@link #userProfile} 委托 {@link UserProfileAggregator}（行为日志→兴趣权重）；
 * 演示走本地 {@link TtlCache}（30s，单飞防击穿 + TTL 打散），生产有 {@link RedisUserProfileStore} 时
 * 优先读 Redis（跨实例共享，天然缓存），miss 现算写回。
 */
@Component
public class InMemoryUserProfileService implements UserProfileService {

    /** 画像缓存 TTL 打散幅度（ms）：实际过期在 [ttl, ttl+jitter) 内随机，防多 key 同时过期惊群 */
    private static final long PROFILE_JITTER_MS = 1_000;

    private final UserProfileAggregator aggregator;
    /** 生产画像 Redis 存储（@Profile("prod") 才存在；演示为 null → 每次现算） */
    @Autowired(required = false)
    private RedisUserProfileStore redisUserProfileStore;

    /** 画像缓存：userId → 画像。构造 ttl=0（禁用），由 setter 注入启用；单飞重建防击穿 */
    private final TtlCache<Long, UserProfile> profileCache = new TtlCache<>(0, PROFILE_JITTER_MS);

    public InMemoryUserProfileService(UserProfileAggregator aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * 画像缓存 TTL（ms），Spring 注入。>0 启用（避免每次请求全量重算画像，"能预计算的不实时算"）；
     * 行为回流后再现算（TTL 兜底），近实时即可；≤0 禁用（测试/调试可关）。
     */
    @Value("${app.rec.profile-cache-ttl-ms:30000}")
    public void setProfileCacheTtlMs(long ttl) {
        profileCache.setTtlMillis(ttl);
    }

    @Override
    public UserProfile userProfile(Long userId) {
        if (redisUserProfileStore != null) {
            // 生产：读 Redis 画像（跨实例共享，天然缓存），未命中现算并写回
            return redisUserProfileStore.get(userId).orElseGet(() -> {
                UserProfile p = aggregator.build(userId);
                redisUserProfileStore.put(userId, p);
                return p;
            });
        }
        // 演示：TtlCache 短缓存（命中免算 + 单飞防击穿 + TTL 打散），ttl<=0 自动退化为每次现算
        return profileCache.get(userId, () -> aggregator.build(userId));
    }
}
