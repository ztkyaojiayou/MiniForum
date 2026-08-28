package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Conversation;

import java.util.List;
import java.util.Optional;

/**
 * 私信会话仓库接口
 * <p>
 * 双实现：{@link InMemoryConversationRepository}（!prod 内存）/ MySqlConversationRepository（prod 行级表 conversations，uk_pair 保证两人唯一）。
 */
public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findById(Long id);

    /** 按会话双方用户名查找（key 规范为 小用户名:大用户名） */
    Optional<Conversation> findByPair(String userX, String userY);

    /** 某用户参与的会话（按最后消息时间倒序） */
    List<Conversation> findByUser(String username);

    /** 导出全部会话（持久化用，按 ID 升序） */
    List<Conversation> exportAll();

    /** 清空并批量导入（从持久化恢复） */
    void importAll(List<Conversation> conversations);

    long count();
}
