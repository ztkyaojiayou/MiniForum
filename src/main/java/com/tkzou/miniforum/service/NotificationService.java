package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.NotificationVO;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 消息通知服务
 * <p>
 * 负责生成通知（被点赞 / 被评论 / 被关注）与通知的查询、已读管理。
 * 生成通知的方法供其他 Service（PostService / CommentService / FollowService）调用。
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * 生成一条通知
     *
     * @param recipientId   接收者用户 ID
     * @param actorId       触发者用户 ID
     * @param actorUsername 触发者用户名
     * @param type          通知类型（LIKE / COMMENT / FOLLOW）
     * @param postId        关联帖子 ID（可为空）
     * @param content       内容摘要
     */
    public void notify(Long recipientId, Long actorId, String actorUsername, String type,
                       Long postId, String content) {
        // 不给自己发通知
        if (recipientId.equals(actorId)) {
            return;
        }
        Notification notification = new Notification();
        notification.setRecipientId(recipientId);
        notification.setActorId(actorId);
        notification.setActorUsername(actorUsername);
        notification.setType(type);
        notification.setPostId(postId);
        notification.setContent(content);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }

    /** 我的通知列表（最新在前） */
    public List<NotificationVO> getMyNotifications(Long recipientId) {
        return notificationRepository.findByRecipientId(recipientId).stream()
                .map(NotificationVO::new)
                .collect(Collectors.toList());
    }

    /** 我的未读通知数 */
    public long getUnreadCount(Long recipientId) {
        return notificationRepository.countUnread(recipientId);
    }

    /** 标记某条通知已读（仅本人可操作） */
    public void markRead(Long id, Long recipientId) {
        Notification notification = notificationRepository.findByRecipientId(recipientId).stream()
                .filter(n -> n.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("通知不存在：id=" + id));
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    /** 全部标记为已读，返回标记条数 */
    public int markAllRead(Long recipientId) {
        return notificationRepository.markAllRead(recipientId);
    }

    /** 删除某帖子的相关通知（帖子删除时由 PostService 级联调用） */
    public void deleteByPostId(Long postId) {
        notificationRepository.deleteByPostId(postId);
    }
}
