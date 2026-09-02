package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Message;

import com.tkzou.miniforum.repository.impl.InMemoryMessageRepository;
import java.util.List;

/**
 * 私信消息仓库接口
 * <p>
 * 双实现：{@link InMemoryMessageRepository}（!prod 内存）/ MySqlMessageRepository（prod 行级表 messages）。
 * 接口化对齐 P1.1 主存储规范化：行为由服务层消费，实现按 @Profile 切换。
 */
public interface MessageRepository {

    Message save(Message message);

    /** 某会话的全部消息（按时间正序，聊天窗口展示顺序） */
    List<Message> findByConversationId(Long conversationId);

    /** 某会话中对方发来且我未读的消息数 */
    long countUnread(Long conversationId, String myUsername);

    /** 某会话中对方发来的消息全部标记为已读，返回条数 */
    int markAllRead(Long conversationId, String myUsername);

    /** 统计我收到的未读消息总数（会话列表角标） */
    long countUnreadForUser(String myUsername);

    /** 导出全部消息（持久化用，按 ID 升序） */
    List<Message> exportAll();

    /** 清空并批量导入（从持久化恢复） */
    void importAll(List<Message> messages);

    long count();
}
