package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.ConversationVO;
import com.tkzou.miniforum.dto.MessageVO;
import com.tkzou.miniforum.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/**
 * 站内私信接口（会话 + 消息，HTTP 轮询，无 WebSocket）
 * <p>
 * 需登录（由 AuthInterceptor 拦截 /api/messages/**）。
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /** 我的会话列表 */
    @GetMapping("/conversations")
    public ResponseEntity<Result<List<ConversationVO>>> getConversations(HttpSession session) {
        String username = (String) session.getAttribute("username");
        Long userId = (Long) session.getAttribute("userId");
        return ResponseEntity.ok(Result.success(messageService.getConversations(username, userId)));
    }

    /** 打开与某用户的会话（不存在则创建），返回会话信息 */
    @GetMapping("/conversations/{peerId}")
    public ResponseEntity<Result<ConversationVO>> openConversation(@PathVariable Long peerId,
                                                                   HttpSession session) {
        String username = (String) session.getAttribute("username");
        Long userId = (Long) session.getAttribute("userId");
        return ResponseEntity.ok(Result.success(messageService.openConversation(username, userId, peerId)));
    }

    /** 某会话的消息列表（时间正序，自动将对方消息标记为已读） */
    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Result<List<MessageVO>>> getMessages(@PathVariable Long conversationId,
                                                               HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(messageService.getMessages(conversationId, username)));
    }

    /** 发送私信（body: {"to": "用户名", "content": "内容"}） */
    @PostMapping
    public ResponseEntity<Result<MessageVO>> send(@RequestBody Map<String, String> body,
                                                  HttpSession session) {
        String username = (String) session.getAttribute("username");
        Long userId = (Long) session.getAttribute("userId");
        MessageVO sent = messageService.send(username, userId, body.get("to"), body.get("content"));
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success("发送成功", sent));
    }

    /** 我收到的未读消息总数（前端轮询用） */
    @GetMapping("/unread-count")
    public ResponseEntity<Result<Map<String, Long>>> getUnreadCount(HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(Map.of("count", messageService.getUnreadTotal(username))));
    }
}
