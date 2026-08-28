package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Message;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存私信消息仓库（默认实现，@Profile("!prod")）
 * 使用 ConcurrentHashMap 保证线程安全。
 */
@Repository
@Profile("!prod")
public class InMemoryMessageRepository implements MessageRepository {
    /** ID 生成器：Spring 注入（演示=实体生成器 / 生产=Snowflake），测试无 Spring 时用默认实体生成器 */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    private final Map<Long, Message> storage = new ConcurrentHashMap<>();

    @Override
    public Message save(Message message) {
        if (message.getId() == null) {
            message.setId(idProvider.next("Message"));
        }
        storage.put(message.getId(), message);
        return message;
    }

    @Override
    public List<Message> findByConversationId(Long conversationId) {
        return storage.values().stream()
                .filter(m -> m.getConversationId().equals(conversationId))
                .sorted(Comparator.comparing(Message::getCreatedAt))
                .collect(Collectors.toList());
    }

    @Override
    public long countUnread(Long conversationId, String myUsername) {
        return storage.values().stream()
                .filter(m -> m.getConversationId().equals(conversationId))
                .filter(m -> !m.isRead())
                .filter(m -> !m.getSender().equals(myUsername))
                .count();
    }

    @Override
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

    @Override
    public long countUnreadForUser(String myUsername) {
        return storage.values().stream()
                .filter(m -> m.getReceiver().equals(myUsername))
                .filter(m -> !m.isRead())
                .count();
    }

    @Override
    public List<Message> exportAll() {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(Message::getId))
                .collect(Collectors.toList());
    }

    @Override
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

    @Override
    public long count() {
        return storage.size();
    }
}
