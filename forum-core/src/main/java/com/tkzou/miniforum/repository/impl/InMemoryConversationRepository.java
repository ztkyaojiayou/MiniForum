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
    /** ID 生成器（构造器注入，P2-26）：Spring 按 profile 注入 EntityIdProvider(!prod) / SnowflakeIdProvider(prod)；测试直构走无参默认 */
    private final IdProvider idProvider;

    /** 测试/默认构造：EntityIdProvider（演示默认） */
    public InMemoryConversationRepository() {
        this(new EntityIdProvider());
    }

    /** 构造器注入：避免 @Autowired(required=false) 字段注入掩盖注入失败 */
    @Autowired
    public InMemoryConversationRepository(IdProvider idProvider) {
        this.idProvider = idProvider;
    }

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

    /**
     * 查找或创建会话（JVM 内原子）：synchronized 使并发下只创建一个会话，
     * 语义与原先 findByPair().orElseGet(save) 完全一致。
     */
    @Override
    public synchronized Conversation findOrCreateByPair(String userX, String userY) {
        return findByPair(userX, userY).orElseGet(() -> save(new Conversation(userX, userY)));
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
