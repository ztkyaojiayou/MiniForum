package com.tkzou.miniforum.repository.impl;
import com.tkzou.miniforum.repository.NotificationRepository;

import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存消息通知仓库（默认实现，@Profile("!prod")）
 * 使用 ConcurrentHashMap 保证线程安全。
 */
@Repository
@Profile("!prod")
public class InMemoryNotificationRepository implements NotificationRepository {
    /** ID 生成器（构造器注入，P2-26）：Spring 按 profile 注入 EntityIdProvider(!prod) / SnowflakeIdProvider(prod)；测试直构走无参默认 */
    private final IdProvider idProvider;

    /** 测试/默认构造：EntityIdProvider（演示默认） */
    public InMemoryNotificationRepository() {
        this(new EntityIdProvider());
    }

    /** 构造器注入：避免 @Autowired(required=false) 字段注入掩盖注入失败 */
    @Autowired
    public InMemoryNotificationRepository(IdProvider idProvider) {
        this.idProvider = idProvider;
    }

    private final Map<Long, Notification> storage = new ConcurrentHashMap<>();

    @Override
    public Notification save(Notification notification) {
        if (notification.getId() == null) {
            notification.setId(idProvider.next("Notification"));
        }
        storage.put(notification.getId(), notification);
        return notification;
    }

    @Override
    public List<Notification> findByRecipientId(Long recipientId) {
        return storage.values().stream()
                .filter(n -> n.getRecipientId().equals(recipientId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Override
    public long countUnread(Long recipientId) {
        return storage.values().stream()
                .filter(n -> n.getRecipientId().equals(recipientId) && !n.isRead())
                .count();
    }

    @Override
    public int markAllRead(Long recipientId) {
        int count = 0;
        for (Notification n : storage.values()) {
            if (n.getRecipientId().equals(recipientId) && !n.isRead()) {
                n.setRead(true);
                count++;
            }
        }
        return count;
    }

    @Override
    public void deleteByPostId(Long postId) {
        storage.entrySet().removeIf(e -> postId.equals(e.getValue().getPostId()));
    }

    @Override
    public List<Notification> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public void importAll(List<Notification> notifications) {
        storage.clear();
        if (notifications != null) {
            for (Notification n : notifications) {
                if (n != null && n.getId() != null) {
                    storage.put(n.getId(), n);
                }
            }
        }
    }
}
