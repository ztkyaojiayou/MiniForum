package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.ConversationVO;
import com.tkzou.miniforum.dto.MessageVO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.repository.InMemoryConversationRepository;
import com.tkzou.miniforum.repository.ConversationRepository;
import com.tkzou.miniforum.repository.InMemoryMessageRepository;
import com.tkzou.miniforum.repository.MessageRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.tkzou.miniforum.repository.InMemoryUserRepository;

/**
 * 站内私信服务单元测试：会话创建 / 消息收发 / 未读数 / 已读标记 / 自禁与用户校验
 */
class MessageServiceTest {

    private MessageService messageService;
    private UserRepository userRepository;
    private MessageRepository messageRepository;

    @BeforeEach
    void setUp() {
        userRepository = new InMemoryUserRepository();
        ConversationRepository conversationRepository = new InMemoryConversationRepository();
        messageRepository = new InMemoryMessageRepository();
        messageService = new MessageService(conversationRepository, messageRepository, userRepository);
    }

    private User createUser(String username, Long id) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return userRepository.save(user);
    }

    @Test
    void send_shouldCreateConversationAndMessage() {
        createUser("alice", 1L);
        createUser("bob", 2L);
        MessageVO message = messageService.send("alice", 1L, "bob", "你好，Bob！");
        assertEquals("alice", message.getSender());
        assertTrue(message.isMine());
        // 会话列表可见
        List<ConversationVO> conversations = messageService.getConversations("alice", 1L);
        assertEquals(1, conversations.size());
        assertEquals("bob", conversations.get(0).getPeer());
    }

    @Test
    void send_shouldIncreaseUnreadForReceiver() {
        createUser("alice", 1L);
        createUser("bob", 2L);
        messageService.send("alice", 1L, "bob", "第一条");
        messageService.send("alice", 1L, "bob", "第二条");
        assertEquals(2, messageService.getUnreadTotal("bob"));
        assertEquals(0, messageService.getUnreadTotal("alice"));
    }

    @Test
    void getMessages_shouldMarkReceivedAsRead() {
        createUser("alice", 1L);
        createUser("bob", 2L);
        messageService.send("alice", 1L, "bob", "你好");
        List<ConversationVO> convs = messageService.getConversations("bob", 2L);
        assertEquals(1, convs.size());
        // 打开会话后未读数清零
        messageService.getMessages(convs.get(0).getId(), "bob");
        assertEquals(0, messageService.getUnreadTotal("bob"));
    }

    @Test
    void send_shouldRejectSelfMessage() {
        createUser("alice", 1L);
        assertThrows(BusinessException.class, () -> messageService.send("alice", 1L, "alice", "自言自语"));
    }

    @Test
    void send_shouldRejectUnknownReceiver() {
        createUser("alice", 1L);
        assertThrows(ResourceNotFoundException.class, () -> messageService.send("alice", 1L, "ghost", "在吗"));
    }

    @Test
    void send_shouldRejectBlankContent() {
        createUser("alice", 1L);
        createUser("bob", 2L);
        assertThrows(BusinessException.class, () -> messageService.send("alice", 1L, "bob", "   "));
    }

    @Test
    void send_shouldRejectTooLongContent() {
        createUser("alice", 1L);
        createUser("bob", 2L);
        String longText = "a".repeat(501);
        assertThrows(BusinessException.class, () -> messageService.send("alice", 1L, "bob", longText));
    }

    @Test
    void openConversation_shouldCreateWhenAbsent() {
        createUser("alice", 1L);
        createUser("bob", 2L);
        ConversationVO vo = messageService.openConversation("alice", 1L, 2L);
        assertTrue(vo.getId() != null);
        assertEquals("bob", vo.getPeer());
    }
}
