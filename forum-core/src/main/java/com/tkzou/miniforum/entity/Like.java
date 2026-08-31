package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 点赞记录实体 —— 【当前状态表】，不是事件历史
 * <p>
 * 一条记录表示「某用户对某帖子点过赞」，同一用户对同一帖子只能有一条记录（唯一约束，防重复点赞）。
 *
 * <h3>设计要点：状态 vs 事件</h3>
 * <ul>
 *   <li><b>本表 = 当前点赞状态</b>：只回答"现在谁赞着谁"。点赞 = INSERT 一行，<b>取消点赞 = DELETE 该行</b>
 *       （不新增"取消"记录、不保留赞/取消赞的历史）——查询"谁赞过"、"我是否赞过"永远准确且 O(1)；</li>
 *   <li><b>历史事件走 BehaviorLog</b>：点赞/取消赞的<b>事件流</b>由 {@code BehaviorLog} 记录
 *       （{@code BehaviorType.LIKE} / {@code BehaviorType.UNLIKE}），供推荐画像/行为分析消费——本表与事件流分工：</li>
 * </ul>
 * <pre>
 *   点赞：Like 表 INSERT + BehaviorLog(LIKE) + likeCount+1
 *   取消：Like 表 DELETE + BehaviorLog(UNLIKE) + likeCount-1
 *   → Like 管"现状"，BehaviorLog 管"发生过什么"，likeCount 是聚合快照（三者各司其职）
 * </pre>
 */
public class Like {

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 帖子 ID */
    private Long postId;

    /** 点赞用户名 */
    private String username;

    /** 点赞时间 */
    private LocalDateTime createdAt;

    public Like() {
    }

    public Like(Long id, Long postId, String username, LocalDateTime createdAt) {
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
