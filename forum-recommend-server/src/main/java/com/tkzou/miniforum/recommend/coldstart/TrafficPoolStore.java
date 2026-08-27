package com.tkzou.miniforum.recommend.coldstart;

import java.util.Map;
import java.util.Optional;

/**
 * 流量池状态存储接口（P2-2 状态外置）
 * <p>
 * 屏蔽"状态放哪"：演示用 {@link InMemoryTrafficPoolStore}（ConcurrentHashMap），
 * 生产用 RedisTrafficPoolStore（JSON + SETNX，多实例共享）。
 * <b>putIfAbsent 是原子入池</b>（对应 notifyCreated 的多 pod 去重）；get/put 读改写跨 pod 非原子，
 * 局限与生产升级路径（HINCRBY/Lua）见 RedisTrafficPoolStore 类注释。
 */
public interface TrafficPoolStore {

    /** 读取某帖状态（无 → empty） */
    Optional<PostState> get(Long postId);

    /** 写入/覆盖状态（InMemory 直接存；Redis 存 JSON 并刷新 TTL） */
    void put(Long postId, PostState state);

    /** 原子创建：仅当不存在时写入，返回是否创建成功（多 pod 去重入池） */
    boolean putIfAbsent(Long postId, PostState state, long ttlSeconds);

    /** 删除某帖状态 */
    void remove(Long postId);

    /** 全量快照（cleanup 遍历用；Redis 实现用 SCAN） */
    Map<Long, PostState> all();

    /** 当前跟踪帖数（测试/监控） */
    int size();
}
