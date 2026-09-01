package com.tkzou.miniforum.repository.impl;
import com.tkzou.miniforum.repository.ConversationRepository;

import com.tkzou.miniforum.entity.Conversation;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存私信会话仓库（默认实现，@Profile("!prod")）
 * 使用 ConcurrentHashMap 保证线程安全。
 */
@Repository
@Profile("!prod")
public class InMemoryConversationRepository implements ConversationRepository {
    /** ID 生成器：Spring 注入（演示=实体生成器 / 生产=Snowflake），测试无 Spring 时用默认实体生成器 */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

    private final Map<Long, Conversation> storage = new ConcurrentHashMap<>();

    @Override
    public Conversation save(Conversation conversation) {
        if (conversation.getId() == null) {
            conversation.setId(idProvider.next("Conversation"));
        }
        storage.put(conversation.getId(), conversation);
        return conversation;
    }

    @Override
    public Optional<Conversation> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public Optional<Conversation> findByPair(String userX, String userY) {
        String key = Conversation.buildKey(userX, userY);
        return storage.values().stream()
                .filter(c -> c.getUserA().equals(userX) || c.getUserB().equals(userX))
                .filter(c -> (c.getUserA() + ":" + c.getUserB()).equals(key))
                .findFirst();
    }

    @Override
    public List<Conversation> findByUser(String username) {
        return storage.values().stream()
                .filter(c -> c.getUserA().equals(username) || c.getUserB().equals(username))
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public List<Conversation> exportAll() {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(Conversation::getId))
                .collect(Collectors.toList());
    }

    @Override
    public void importAll(List<Conversation> conversations) {
        storage.clear();
        if (conversations != null) {
            for (Conversation c : conversations) {
                if (c != null && c.getId() != null) {
                    storage.put(c.getId(), c);
                }
            }
        }
    }

    @Override
    public long count() {
        return storage.size();
    }
}
