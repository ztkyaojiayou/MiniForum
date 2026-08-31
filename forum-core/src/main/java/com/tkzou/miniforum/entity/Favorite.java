package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 收藏记录实体 —— 【当前状态表】，不是事件历史（同 {@link Like} 的设计）
 * <p>
 * 一条记录表示「某用户收藏了某帖子」，同一用户对同一帖子只能有一条记录。
 * 收藏 = INSERT、取消收藏 = DELETE（不保留历史）；收藏/取消收藏的<b>事件流</b>由 {@code BehaviorLog}
 * 记录（{@code FAVORITE} / {@code UNFAVORITE}），本表只管"当前收藏状态"。
 */
public class Favorite {

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 帖子 ID */
    private Long postId;

    /** 收藏用户名 */
    private String username;

    /** 收藏时间 */
    private LocalDateTime createdAt;

    public Favorite() {
    }

    public Favorite(Long id, Long postId, String username, LocalDateTime createdAt) {
        this.id = id;
        this.postId = postId;
        this.username = username;
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

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
