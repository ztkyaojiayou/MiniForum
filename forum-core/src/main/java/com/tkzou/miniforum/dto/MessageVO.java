package com.tkzou.miniforum.dto;

import com.tkzou.miniforum.entity.Message;

import java.time.LocalDateTime;

/**
 * 私信消息视图对象
 */
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public Long getSenderId() {
        return senderId;
    }

    public void setSenderId(Long senderId) {
        this.senderId = senderId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isMine() {
        return mine;
    }

    public void setMine(boolean mine) {
        this.mine = mine;
    }
}
