package com.tkzou.miniforum.dto.response;
import lombok.Getter;
import lombok.Setter;

import com.tkzou.miniforum.entity.Notification;

import java.time.LocalDateTime;

/**
 * 消息通知视图对象（返回给前端）
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
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

}
