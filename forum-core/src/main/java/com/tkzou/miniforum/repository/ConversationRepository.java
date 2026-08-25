package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Conversation;
import org.springframework.stereotype.Repository;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的私信会话仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class ConversationRepository {

    private final Map<Long, Conversation> storage = new ConcurrentHashMap<>();

    public Conversation save(Conversation conversation) {
        if (conversation.getId() == null) {
            conversation.setId(Conversation.nextId());
        }
        storage.put(conversation.getId(), conversation);
        return conversation;
    }

    public Optional<Conversation> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    /** 按会话双方用户名查找（key 规范为 小用户名:大用户名） */
    public Optional<Conversation> findByPair(String userX, String userY) {
        String key = Conversation.buildKey(userX, userY);
        return storage.values().stream()
                .filter(c -> c.getUserA().equals(userX) || c.getUserB().equals(userX))
                .filter(c -> (c.getUserA() + ":" + c.getUserB()).equals(key))
                .findFirst();
    }

    /** 某用户参与的会话（按最后消息时间倒序） */
    public List<Conversation> findByUser(String username) {
        return storage.values().stream()
                .filter(c -> c.getUserA().equals(username) || c.getUserB().equals(username))
                .sorted(Comparator.comparing(Conversation::getLastMessageAt).reversed())
                .collect(Collectors.toList());
    }

    /** 导出全部会话（用于持久化，按 ID 升序） */
    public List<Conversation> exportAll() {
        return storage.values().stream()
                .sorted(Comparator.comparingLong(Conversation::getId))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    public void importAll(List<Conversation> conversations) {
        storage.clear();
        if (conversations != null) {
            for (Conversation c : conversations) {
                if (c != null && c.getId() != null) {
                    storage.put(c.getId(), c);
                }
            }
        }
    }

    public long count() {
        return storage.size();
    }
}
