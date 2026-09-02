package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Notification;

import com.tkzou.miniforum.repository.impl.InMemoryNotificationRepository;
import java.util.List;

/**
 * 消息通知仓库接口
 * <p>
 * 双实现：{@link InMemoryNotificationRepository}（!prod 内存）/ MySqlNotificationRepository（prod 行级表 notifications）。
 */
public interface NotificationRepository {

    Notification save(Notification notification);

    /** 某用户的通知（最新在前） */
    List<Notification> findByRecipientId(Long recipientId);

    /** 某用户的未读通知数 */
    long countUnread(Long recipientId);

    /** 将某用户的全部通知标记为已读 */
    int markAllRead(Long recipientId);

    /** 删除某帖子相关的全部通知（帖子被删除时级联清理） */
    void deleteByPostId(Long postId);

    /** 导出全部通知（持久化用，按 ID 升序） */
    List<Notification> exportAll();

    /** 清空并批量导入（从持久化恢复） */
    void importAll(List<Notification> notifications);
}
