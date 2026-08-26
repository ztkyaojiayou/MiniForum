package com.tkzou.miniforum.idempotency;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存幂等存储（默认实现，@Profile("!prod")）
 * <p>
 * key → {value, expireAt}；value 为 "PROCESSING"（占位中）或 postId 字符串。
 * {@link #acquire} 用 putIfAbsent 原子占位，过期占位用 replace 原子替换；
 * TTL 用 app.idempotency.ttl-ms 惰性过期（读取/占位时清理）。
 */
@Component
@Profile("!prod")
public class InMemoryIdempotencyStore implements IdempotencyStore {

    private static final String PROCESSING = "PROCESSING";

    private final long ttlMs;

    private final Map<String, Entry> storage = new ConcurrentHashMap<>();

    public InMemoryIdempotencyStore(@Value("${app.idempotency.ttl-ms:300000}") long ttlMs) {
        this.ttlMs = ttlMs;
    }

    @Override
    public Optional<Long> getCompleted(String key) {
        Entry entry = entryOf(key);
        if (entry == null || PROCESSING.equals(entry.value)) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(entry.value));
    }

    @Override
    public boolean acquire(String key) {
        Entry fresh = new Entry(PROCESSING, now() + ttlMs);
        Entry existed = storage.putIfAbsent(key, fresh);
        if (existed == null) {
            return true; // 首次占位成功
        }
        if (isExpired(existed)) {
            // 过期占位：原子替换为新的占位（同 key 超时后可重新提交）
            return storage.replace(key, existed, fresh);
        }
        return false; // 正在处理或已完成
    }

    @Override
    public void complete(String key, Long postId) {
        storage.put(key, new Entry(String.valueOf(postId), now() + ttlMs));
    }

    @Override
    public void release(String key) {
        storage.remove(key);
    }

    /** 惰性清理过期条目后返回当前条目 */
    private Entry entryOf(String key) {
        Entry entry = storage.get(key);
        if (entry == null) {
            return null;
        }
        if (isExpired(entry)) {
            storage.remove(key);
            return null;
        }
        return entry;
    }

    private boolean isExpired(Entry entry) {
        return entry.expireAt < now();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static final class Entry {
        final String value;
        final long expireAt;

        Entry(String value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }
}
