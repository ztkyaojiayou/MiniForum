package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 私信消息实体
 * <p>
 * 属于某个会话（{@link Conversation}），记录发送者、接收者、内容与已读状态。
 * 纯内存存储，可 JSON 持久化。
 */
public class Message {

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 所属会话 ID */
    private Long conversationId;

    /** 发送者用户名 */
    private String sender;

    /** 发送者用户 ID */
    private Long senderId;

    /** 接收者用户名 */
    private String receiver;

    /** 接收者用户 ID */
    private Long receiverId;

    /** 消息内容 */
    private String content;

    /** 发送时间 */
    private LocalDateTime createdAt;

    /** 接收者是否已读 */
    private boolean read;

    public Message() {
    }

    /** 生成下一个自增 ID */
    public static Long nextId() {
        return ID_GENERATOR.getAndIncrement();
    }

    /** 将 ID 生成器推进到指定最小值之后（用于从持久化数据恢复，避免 ID 冲突） */
    public static synchronized void resetIdGenerator(long minId) {
        ID_GENERATOR.set(Math.max(ID_GENERATOR.get(), minId + 1));
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getConversationId() {
        return conversationId;
    }

    public void setConversationId(Long conversationId) {
        this.conversationId = conversationId;
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

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public Long getReceiverId() {
        return receiverId;
    }

    public void setReceiverId(Long receiverId) {
        this.receiverId = receiverId;
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
}
