package com.tkzou.miniforum.service;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.util.TtlCache;

import java.util.function.Supplier;

/**
 * 单帖查询缓存（P2-21 拆分用）：读路径（getById）与写路径（update/delete/like 等失效）<b>共用</b>的共享组件。
 * <p>
 * 拆分 PostCommandService / PostQueryService 后，若不共享此缓存，两个服务各持一份 TtlCache，
 * 写失效（invalidate）就传播不到读缓存，导致 P1-15 修的缓存语义被破坏。
 */
public class PostQueryCache {

    /** 单帖实体缓存 TTL 打散幅度（ms） */
    private static final long POST_CACHE_JITTER_MS = 1_000;
    private final TtlCache<Long, Post> postCache = new TtlCache<>(0, POST_CACHE_JITTER_MS);

    /** TTL（ms）；>0 启用，≤0 禁用（每次回源）。由 PostService 门面的 @Value setter 注入。 */
    public void setPostCacheTtlMs(long ttl) {
        postCache.setTtlMillis(ttl);
    }

    /** 读：命中缓存则直接返回，否则用 loader 回源并缓存（防御性拷贝由调用方保证） */
    public Post get(Long id, Supplier<Post> loader) {
        return postCache.get(id, loader);
    }

    /** 写失效：帖子内容/计数变化时踢掉缓存，下次读回源 */
    public void invalidate(Long id) {
        postCache.invalidate(id);
    }
}
