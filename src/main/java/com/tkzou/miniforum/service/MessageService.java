package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.ConversationVO;
import com.tkzou.miniforum.dto.MessageVO;
import com.tkzou.miniforum.entity.Conversation;
import com.tkzou.miniforum.entity.Message;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.repository.ConversationRepository;
import com.tkzou.miniforum.repository.MessageRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内私信服务
 * <p>
 * 会话 + 消息双实体：发送私信时自动查找或创建会话，会话列表按最后消息时间倒序，
 * 消息按时间正序展示。前端通过轮询 {@code /api/messages/unread-count} 获取未读数。
 * 不依赖任何第三方中间件（无 WebSocket，走 HTTP 轮询）。
 */
@Service
public class MessageService {

    /** 消息内容最大长度 */
    private static final int MAX_CONTENT_LENGTH = 500;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public MessageService(ConversationRepository conversationRepository,
                          MessageRepository messageRepository,
                          UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    /** 我的会话列表（按最后消息时间倒序，含对方信息与未读数） */
    public List<ConversationVO> getConversations(String myUsername, Long myId) {
        return conversationRepository.findByUser(myUsername).stream()
                .map(c -> toConversationVO(c, myUsername))
                .collect(Collectors.toList());
    }

    /** 打开与某用户的会话：不存在则创建，返回会话信息 */
    public ConversationVO openConversation(String myUsername, Long myId, Long peerId) {
        User peer = userRepository.findById(peerId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在：id=" + peerId));
        if (peer.getUsername().equals(myUsername)) {
            throw new BusinessException("不能给自己发私信");
        }
        Conversation conversation = conversationRepository.findByPair(myUsername, peer.getUsername())
                .orElseGet(() -> conversationRepository.save(new Conversation(myUsername, peer.getUsername())));
        return toConversationVO(conversation, myUsername);
    }

    /** 某会话的消息列表（时间正序），同时将对方发来的消息标记为已读 */
    public List<MessageVO> getMessages(Long conversationId, String myUsername) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("会话不存在：id=" + conversationId));
        if (!conversation.getUserA().equals(myUsername) && !conversation.getUserB().equals(myUsername)) {
            throw new BusinessException("无权查看该会话");
        }
        messageRepository.markAllRead(conversationId, myUsername);
        return messageRepository.findByConversationId(conversationId).stream()
                .map(m -> new MessageVO(m, myUsername))
                .collect(Collectors.toList());
    }

    /** 发送私信：查找或创建会话，保存消息并更新会话摘要 */
    public MessageVO send(String myUsername, Long myId, String toUsername, String content) {
        if (content == null || content.isBlank()) {
            throw new BusinessException("消息内容不能为空");
        }
        String text = content.trim();
        if (text.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException("消息不能超过 " + MAX_CONTENT_LENGTH + " 字");
        }
        User receiver = userRepository.findByUsername(toUsername)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在：" + toUsername));
        if (receiver.getUsername().equals(myUsername)) {
            throw new BusinessException("不能给自己发私信");
        }
        Conversation conversation = conversationRepository.findByPair(myUsername, toUsername)
                .orElseGet(() -> conversationRepository.save(new Conversation(myUsername, toUsername)));
        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSender(myUsername);
        message.setSenderId(myId);
        message.setReceiver(toUsername);
        message.setReceiverId(receiver.getId());
        message.setContent(text);
        message.setCreatedAt(LocalDateTime.now());
        message.setRead(false);
        Message saved = messageRepository.save(message);
        // 更新会话摘要
        conversation.setLastMessageAt(saved.getCreatedAt());
        conversation.setLastMessage(text.length() > 50 ? text.substring(0, 50) + "..." : text);
        conversation.setLastSender(myUsername);
        conversationRepository.save(conversation);
        return new MessageVO(saved, myUsername);
    }

    /** 我收到的未读消息总数（前端轮询角标） */
    public long getUnreadTotal(String myUsername) {
        return messageRepository.countUnreadForUser(myUsername);
    }

    private ConversationVO toConversationVO(Conversation c, String myUsername) {
        ConversationVO vo = new ConversationVO();
        vo.setId(c.getId());
        String peerName = c.getUserA().equals(myUsername) ? c.getUserB() : c.getUserA();
        vo.setPeer(peerName);
        userRepository.findByUsername(peerName).ifPresent(u -> vo.setPeerId(u.getId()));
        vo.setLastMessage(c.getLastMessage());
        vo.setLastSender(c.getLastSender());
        vo.setLastMessageAt(c.getLastMessageAt());
        vo.setUnreadCount(messageRepository.countUnread(c.getId(), myUsername));
        return vo;
    }
}
