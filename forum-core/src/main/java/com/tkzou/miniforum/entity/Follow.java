package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 关注关系实体 —— 【当前状态表】，不是事件历史
 * <p>
 * 一条记录表示「followerId 关注了 followeeId」，同一对用户只能有一条关注记录。
 * 关注 = INSERT、取关 = DELETE（不保留历史）；关注/取关的<b>事件流</b>由 {@code BehaviorLog}
 * 记录（{@code FOLLOW} / {@code UNFOLLOW}），本表只管"当前关注关系"（社交图的有向边）。
 */
public class Follow {

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 关注者用户 ID */
    private Long followerId;

    /** 被关注者用户 ID */
    private Long followeeId;

    /** 关注时间 */
    private LocalDateTime createdAt;

    public Follow() {
    }

    public Follow(Long id, Long followerId, Long followeeId, LocalDateTime createdAt) {
        this.id = id;
        this.followerId = followerId;
        this.followeeId = followeeId;
        this.createdAt = createdAt;
    }

    /** 生成下一个自增 ID */
    public static Long nextId() {
        return ID_GENERATOR.getAndIncrement();
    }

    /**
     * 将 ID 生成器推进到指定最小值之后（用于从持久化数据恢复，避免 ID 冲突）
     */
    public static synchronized void resetIdGenerator(long minId) {
        ID_GENERATOR.set(Math.max(ID_GENERATOR.get(), minId + 1));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getFollowerId() {
        return followerId;
    }

    public void setFollowerId(Long followerId) {
        this.followerId = followerId;
    }

    public Long getFolloweeId() {
        return followeeId;
    }

    public void setFolloweeId(Long followeeId) {
        this.followeeId = followeeId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
