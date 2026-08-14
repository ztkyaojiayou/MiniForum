package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.User;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 内存存储的用户仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
public class UserRepository {

    private final Map<Long, User> storage = new ConcurrentHashMap<>();

    public User save(User user) {
        if (user.getId() == null) {
            user.setId(User.nextId());
        }
        storage.put(user.getId(), user);
        return user;
    }

    public Optional<User> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    public Optional<User> findByUsername(String username) {
        return storage.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    public List<User> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    public void deleteById(Long id) {
        storage.remove(id);
    }

    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    /** 导出全部用户（用于持久化，按 ID 升序） */
    public List<User> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    public void importAll(List<User> users) {
        storage.clear();
        if (users != null) {
            for (User u : users) {
                if (u != null && u.getId() != null) {
                    storage.put(u.getId(), u);
                }
            }
        }
    }

    public long count() {
        return storage.size();
    }
}
