package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Message;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的私信消息仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class MessageRepository {

    private final Map<Long, Message> storage = new ConcurrentHashMap<>();

    public Message save(Message message) {
        if (message.getId() == null) {
            message.setId(Message.nextId());
        }
        storage.put(message.getId(), message);
        return message;
    }

    /** 某会话的全部消息（按时间正序，聊天窗口展示顺序） */
    public List<Message> findByConversationId(Long conversationId) {
        return storage.values().stream()
                .filter(m -> m.getConversationId().equals(conversationId))
                .sorted(Comparator.comparing(Message::getCreatedAt))
                .collect(Collectors.toList());
    }

    /** 某会话中对方发来且我未读的消息数 */
    public long countUnread(Long conversationId, String myUsername) {
        return storage.values().stream()
                .filter(m -> m.getConversationId().equals(conversationId))
                .filter(m -> !m.isRead())
                .filter(m -> !m.getSender().equals(myUsername))
                .count();
    }

    /** 某会话中对方发来的消息全部标记为已读，返回条数 */
    public int markAllRead(Long conversationId, String myUsername) {
        int count = 0;
        for (Message m : storage.values()) {
            if (m.getConversationId().equals(conversationId)
                    && !m.isRead() && !m.getSender().equals(myUsername)) {
                m.setRead(true);
                count++;
            }
        }
        return count;
    }

    /** 统计我收到的未读消息总数（会话列表角标） */
    public long countUnreadForUser(String myUsername) {
        return storage.values().stream()
                .filter(m -> m.getReceiver().equals(myUsername))
                .filter(m -> !m.isRead())
                .count();
    }

    /** 导出全部消息（用于持久化，按 ID 升序） */
    public List<Message> exportAll() {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(Message::getId))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    public void importAll(List<Message> messages) {
        storage.clear();
        if (messages != null) {
            for (Message m : messages) {
                if (m != null && m.getId() != null) {
                    storage.put(m.getId(), m);
                }
            }
        }
    }

    public long count() {
        return storage.size();
    }
}
