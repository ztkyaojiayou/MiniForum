package com.tkzou.miniforum.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * 短 TTL 缓存组件（P1 缓存与防击穿共用，零第三方依赖）
 * <p>
 * 三防能力：
 * <ul>
 *   <li><b>命中即免算</b>：TTL 内返回缓存值，loader 只执行一次（"能预计算的不实时算"）；</li>
 *   <li><b>单飞重建防击穿</b>：miss/过期时用 {@code ConcurrentHashMap.compute} 在 key 的 bin 锁内重建，
 *       同一 key 并发只有一条线程跑 loader，其余拿到新值（热点 key 过期瞬间不会打爆下游）；</li>
 *   <li><b>TTL 随机打散防雪崩</b>：实际过期 = now + ttl + [0, jitter)，避免多 key 同时过期触发惊群。</li>
 * </ul>
 * 降级开关：{@link #setTtlMillis} 置 0 即禁用（每次现算），可运行时动态切换。
 * loader 抛异常时 {@code compute} 不落缓存条目（不毒化），下次调用重试。
 * 键基数有界（postId/userId/单 key 榜单），无需清理线程；惰性过期。
 */
public final class TtlCache<K, V> {

    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();

    /** TTL 打散幅度（毫秒）：实际过期在 [ttl, ttl+jitter) 内随机，>0 才生效 */
    private final long jitterMillis;

    /** 缓存存活时间（毫秒）；<=0 视为禁用（每次现算） */
    private volatile long ttlMillis;

    public TtlCache(long ttlMillis) {
        this(ttlMillis, 0);
    }

    public TtlCache(long ttlMillis, long jitterMillis) {
        this.ttlMillis = ttlMillis;
        this.jitterMillis = jitterMillis;
    }

    /** 取值：命中返回缓存；miss/过期单飞重建；ttl<=0 直接现算（禁用） */
    public V get(K key, Supplier<V> loader) {
        long ttl = ttlMillis;
        if (ttl <= 0) {
            return loader.get();
        }
        long now = System.currentTimeMillis();
        Entry<V> cur = map.get(key);
        if (cur != null && !cur.expired(now)) {
            return cur.value;
        }
        // miss/过期 → 单飞重建：compute 持 key 所在 bin 锁，同 key 只有一个 loader 执行；
        // 若别的线程已重建（existing 未过期）则直接复用，loader 不再执行。
        Entry<V> computed = map.compute(key, (k, existing) -> {
            long now2 = System.currentTimeMillis();
            if (existing != null && !existing.expired(now2)) {
                return existing;
            }
            return new Entry<>(loader.get(), now2 + ttl + jitter());
        });
        return computed.value;
    }

    /** 显式淘汰某 key（行为回流等强一致场景可主动失效） */
    public void invalidate(K key) {
        map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }

    public long getTtlMillis() {
        return ttlMillis;
    }

    /** 动态改 TTL：置 0 禁用、正值重新启用（测试/降级开关） */
    public void setTtlMillis(long ttlMillis) {
        this.ttlMillis = ttlMillis;
    }

    /** TTL 随机打散：+[0, jitter) 毫秒，防多 key 同一时刻集体过期 */
    private long jitter() {
        return jitterMillis > 0 ? ThreadLocalRandom.current().nextLong(jitterMillis) : 0L;
    }

    /** 缓存条目：值 + 到期时间戳（惰性过期） */
    private static final class Entry<V> {
        final V value;
        final long expireAtMillis;

        Entry(V value, long expireAtMillis) {
            this.value = value;
            this.expireAtMillis = expireAtMillis;
        }

        boolean expired(long now) {
            return now > expireAtMillis;
        }
    }
}
