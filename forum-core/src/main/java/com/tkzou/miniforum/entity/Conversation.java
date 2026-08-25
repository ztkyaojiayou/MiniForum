package com.tkzou.miniforum.entity;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 私信会话实体
 * <p>
 * 两个用户之间唯一的会话：userA / userB 按用户名字典序存储（userA < userB），
 * 保证同一对用户只会有一个会话。纯内存存储，可 JSON 持久化。
 */
public class Conversation {

    /** 自增 ID 生成器（内存存储用） */
    private static final AtomicLong ID_GENERATOR = new AtomicLong(1);

    private Long id;

    /** 会话方 A（用户名，字典序较小） */
    private String userA;

    /** 会话方 B（用户名，字典序较大） */
    private String userB;

    /** 最后一条消息时间（会话列表按此倒序） */
    private LocalDateTime lastMessageAt;

    /** 最后一条消息内容摘要（会话列表预览） */
    private String lastMessage;

    /** 最后一条消息发送者用户名 */
    private String lastSender;

    public Conversation() {
    }

    public Conversation(String userX, String userY) {
        if (userX.compareTo(userY) <= 0) {
            this.userA = userX;
            this.userB = userY;
        } else {
            this.userA = userY;
            this.userB = userX;
        }
        this.lastMessageAt = LocalDateTime.now();
    }

    /** 规范化会话键：小用户名 + ":" + 大用户名（保证同一对用户唯一） */
    public static String buildKey(String userX, String userY) {
        return userX.compareTo(userY) <= 0 ? userX + ":" + userY : userY + ":" + userX;
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

    public String getUserA() {
        return userA;
    }

    public void setUserA(String userA) {
        this.userA = userA;
    }

    public String getUserB() {
        return userB;
    }

    public void setUserB(String userB) {
        this.userB = userB;
    }

    public LocalDateTime getLastMessageAt() {
        return lastMessageAt;
    }

    public void setLastMessageAt(LocalDateTime lastMessageAt) {
        this.lastMessageAt = lastMessageAt;
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
}
