package com.tkzou.miniforum.recommend.feature.impl;
import com.tkzou.miniforum.recommend.feature.RealtimeFeature;
import com.tkzou.miniforum.recommend.feature.RealtimeFeatureStore;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实时特征存储（默认实现，模拟 Redis）
 * <p>
 * key → RealtimeFeature 的内存 Map，由 {@code RealtimeFeatureWindow} 写入、在线层读取。
 * 生产形态见 prod.redis.RedisRealtimeFeatureStore（@Profile("prod")）。
 */
@Component
@Profile("!prod")
public class InMemoryRealtimeFeatureStore implements RealtimeFeatureStore {

    private final Map<String, RealtimeFeature> store = new ConcurrentHashMap<>();

    @Override
    public void put(String key, RealtimeFeature feature) {
        store.put(key, feature);
    }

    @Override
    public Optional<RealtimeFeature> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    /** 当前存储的 key 数量（测试/监控） */
    public int size() {
        return store.size();
    }

    /** 清空（测试用） */
    public void clear() {
        store.clear();
    }
}
