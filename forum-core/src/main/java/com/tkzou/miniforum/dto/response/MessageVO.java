package com.tkzou.miniforum.dto.response;
import lombok.Getter;
import lombok.Setter;

import com.tkzou.miniforum.entity.Message;

import java.time.LocalDateTime;

/**
 * 私信消息视图对象
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class MessageVO {

    private Long id;

    /** 发送者用户名 */
    private String sender;

    /** 发送者用户 ID */
    private Long senderId;

    /** 消息内容 */
    private String content;

    /** 发送时间 */
    private LocalDateTime createdAt;

    /** 是否已读 */
    private boolean read;

    /** 是否由当前登录用户发出（前端左右气泡用） */
    private boolean mine;

    public MessageVO() {
    }

    public MessageVO(Message message, String myUsername) {
        this.id = message.getId();
        this.sender = message.getSender();
        this.senderId = message.getSenderId();
        this.content = message.getContent();
        this.createdAt = message.getCreatedAt();
        this.read = message.isRead();
        this.mine = myUsername != null && myUsername.equals(message.getSender());
    }

}
