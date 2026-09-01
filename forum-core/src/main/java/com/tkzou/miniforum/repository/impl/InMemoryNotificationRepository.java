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
    /** ID 生成器：Spring 注入（演示=实体生成器 / 生产=Snowflake），测试无 Spring 时用默认实体生成器 */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();

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
