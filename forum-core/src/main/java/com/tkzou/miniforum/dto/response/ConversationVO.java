package com.tkzou.miniforum.dto.response;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 私信会话视图对象（会话列表用）
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
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

}
