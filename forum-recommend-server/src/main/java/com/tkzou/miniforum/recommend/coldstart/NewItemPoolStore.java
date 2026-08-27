package com.tkzou.miniforum.recommend.coldstart;

import java.util.Optional;

/**
 * Thompson 后验状态存储接口（P2-2 状态外置）
 * <p>
 * 屏蔽"α/β 放哪"：演示用 {@link InMemoryNewItemPoolStore}（ConcurrentHashMap），
 * 生产用 RedisNewItemPoolStore（JSON，多实例共享）。读改写跨 pod 非原子，局限见 Redis 实现注释。
 */
public interface NewItemPoolStore {

    /** 读取某物品的后验参数（无 → empty） */
    Optional<AlphaBeta> get(Long itemId);

    /** 写入/覆盖后验参数 */
    void put(Long itemId, AlphaBeta ab);

    /** 原子创建：仅当不存在时写入，返回是否创建成功 */
    boolean putIfAbsent(Long itemId, AlphaBeta ab, long ttlSeconds);

    /** 删除 */
    void remove(Long itemId);

    /** 是否已跟踪（进程内/Redis 是否存在该 key） */
    boolean containsKey(Long itemId);
}
