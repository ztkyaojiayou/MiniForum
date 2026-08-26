package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Notification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 内存存储的消息通知仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class NotificationRepository {
    /** ID 生成器：Spring 注入（演示=实体生成器 / 生产=Snowflake），测试无 Spring 时用默认实体生成器 */
    @Autowired(required = false)
    private IdProvider idProvider = new EntityIdProvider();


    private final Map<Long, Notification> storage = new ConcurrentHashMap<>();

    public Notification save(Notification notification) {
        if (notification.getId() == null) {
            notification.setId(idProvider.next("Notification"));
        }
        storage.put(notification.getId(), notification);
        return notification;
    }

    /** 某用户的通知（最新在前） */
    public List<Notification> findByRecipientId(Long recipientId) {
        return storage.values().stream()
                .filter(n -> n.getRecipientId().equals(recipientId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 某用户的未读通知数 */
    public long countUnread(Long recipientId) {
        return storage.values().stream()
                .filter(n -> n.getRecipientId().equals(recipientId) && !n.isRead())
                .count();
    }

    /** 将某用户的全部通知标记为已读 */
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

    /** 删除某帖子相关的全部通知（帖子被删除时级联清理） */
    public void deleteByPostId(Long postId) {
        storage.entrySet().removeIf(e -> postId.equals(e.getValue().getPostId()));
    }

    /** 导出全部通知（用于持久化，按 ID 升序） */
    public List<Notification> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
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
