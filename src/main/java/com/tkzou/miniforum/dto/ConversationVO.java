package com.tkzou.miniforum.dto;

import java.time.LocalDateTime;

/**
 * 私信会话视图对象（会话列表用）
 */
public class ConversationVO {

    private Long id;

    /** 对方用户名 */
    private String peer;

    /** 对方用户 ID */
    private Long peerId;

    /** 最后一条消息内容摘要 */
    private String lastMessage;

    /** 最后一条消息发送者 */
    private String lastSender;

    /** 最后一条消息时间 */
    private LocalDateTime lastMessageAt;

    /** 我在此会话中的未读数 */
    private long unreadCount;

    public ConversationVO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPeer() {
        return peer;
    }

    public void setPeer(String peer) {
        this.peer = peer;
    }

    public Long getPeerId() {
        return peerId;
    }

    public void setPeerId(Long peerId) {
        this.peerId = peerId;
    }

    public String getLastMessage() {
        return lastMessage;
    }

    public void setLastMessage(String lastMessage) {
        this.lastMessage = lastMessage;
    }

    public String getLastSender() {
        return lastSender;
    }

    public void setLastSender(String lastSender) {
        this.lastSender = lastSender;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
    }

    public long getUnreadCount() {
        return unreadCount;
    }

    public void setUnreadCount(long unreadCount) {
        this.unreadCount = unreadCount;
    }
}
