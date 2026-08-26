package com.tkzou.miniforum.idempotency;

import java.util.Optional;

/**
 * 发帖幂等存储（Idempotency-Key）
 * <p>
 * 防双击/重试发重：客户端每次"逻辑提交"带一个 UUID key，服务端按 key 去重。
 * 流程：{@link #acquire}（原子占位）→ 发帖 → {@link #complete}（写回 postId）；
 * 重复提交 {@link #getCompleted} 命中直接返回首次结果。
 * <p>
 * 双实现：内存 {@link InMemoryIdempotencyStore}（@Profile("!prod")，演示）/
 * Redis {@link RedisIdempotencyStore}（@Profile("prod")，NX+EX 原子 + TTL）。
 */
public interface IdempotencyStore {

    /** key 已完成 → 返回首次发帖的 postId（幂等结果） */
    Optional<Long> getCompleted(String key);

    /** 原子占位（NX）：拿到返回 true；已在处理或已完成返回 false */
    boolean acquire(String key);

    /** 发帖成功后写回 postId（带 TTL，过期后可重新提交） */
    void complete(String key, Long postId);

    /** 发帖失败释放占位 */
    void release(String key);
}
