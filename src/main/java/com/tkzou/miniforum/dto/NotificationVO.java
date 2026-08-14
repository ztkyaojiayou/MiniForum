package com.tkzou.miniforum.dto;

import com.tkzou.miniforum.entity.Notification;

import java.time.LocalDateTime;

/**
 * 消息通知视图对象（返回给前端）
 */
public class NotificationVO {

    private Long id;

    /** 触发通知的用户 ID */
    private Long actorId;

    /** 触发通知的用户名 */
    private String actorUsername;

    /** 通知类型：LIKE / COMMENT / FOLLOW */
    private String type;

    /** 关联帖子 ID（可为空） */
    private Long postId;

    /** 通知内容摘要 */
    private String content;

    /** 是否已读 */
    private boolean read;

    /** 通知时间 */
    private LocalDateTime createdAt;

    public NotificationVO() {
    }

    public NotificationVO(Notification n) {
        this.id = n.getId();
        this.actorId = n.getActorId();
        this.actorUsername = n.getActorUsername();
        this.type = n.getType();
        this.postId = n.getPostId();
        this.content = n.getContent();
        this.read = n.isRead();
        this.createdAt = n.getCreatedAt();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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
