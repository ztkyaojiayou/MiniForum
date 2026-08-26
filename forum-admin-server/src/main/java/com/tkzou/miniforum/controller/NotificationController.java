package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.NotificationVO;
import com.tkzou.miniforum.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/**
 * 通知中心接口（/api/notifications，需登录）
 * <p>
 * 被点赞/评论/关注/转发/@提及的通知列表、未读数、单条已读/全部已读。
 * 通知由业务 service（点赞/评论/关注/转发/@提及）触发写入，前端轮询未读数。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** 我的通知列表（最新在前） */
    @GetMapping
    public ResponseEntity<Result<List<NotificationVO>>> getMyNotifications(HttpSession session) {
        Long me = (Long) session.getAttribute("userId");
        return ResponseEntity.ok(Result.success(notificationService.getMyNotifications(me)));
    }

    /** 我的未读通知数 */
    @GetMapping("/unread-count")
    public ResponseEntity<Result<Map<String, Long>>> getUnreadCount(HttpSession session) {
        Long me = (Long) session.getAttribute("userId");
        return ResponseEntity.ok(Result.success(Map.of("count", notificationService.getUnreadCount(me))));
    }

    /** 标记某条通知为已读 */
    @PutMapping("/{id}/read")
    public ResponseEntity<Result<Void>> markRead(@PathVariable Long id, HttpSession session) {
        Long me = (Long) session.getAttribute("userId");
        notificationService.markRead(id, me);
        return ResponseEntity.ok(Result.success("已读", null));
    }

    /** 全部标记为已读 */
    @PutMapping("/read-all")
    public ResponseEntity<Result<Map<String, Integer>>> markAllRead(HttpSession session) {
        Long me = (Long) session.getAttribute("userId");
        int count = notificationService.markAllRead(me);
        return ResponseEntity.ok(Result.success(Map.of("count", count)));
    }
}
