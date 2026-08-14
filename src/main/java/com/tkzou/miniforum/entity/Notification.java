package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 消息通知实体
 * <p>
 * 当用户被点赞、被评论、被关注时生成一条通知，支持未读/已读状态。
 */
public class Notification {

    /** 通知类型：点赞 */
    public static final String TYPE_LIKE = "LIKE";
    /** 通知类型：评论 */
    public static final String TYPE_COMMENT = "COMMENT";
    /** 通知类型：关注 */
    public static final String TYPE_FOLLOW = "FOLLOW";

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 接收通知的用户 ID */
    private Long recipientId;

    /** 触发通知的用户 ID */
    private Long actorId;

    /** 触发通知的用户名 */
    private String actorUsername;

    /** 通知类型：LIKE / COMMENT / FOLLOW */
    private String type;

    /** 关联帖子 ID（点赞/评论类通知，可为空） */
    private Long postId;

    /** 通知内容摘要 */
    private String content;

    /** 是否已读 */
    private boolean read;

    /** 通知时间 */
    private LocalDateTime createdAt;

    public Notification() {
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

    public Long getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(Long recipientId) {
        this.recipientId = recipientId;
    }

    public Long getActorId() {
        return actorId;
    }

    public void setActorId(Long actorId) {
        this.actorId = actorId;
    }

    public String getActorUsername() {
        return actorUsername;
    }

    public void setActorUsername(String actorUsername) {
        this.actorUsername = actorUsername;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
