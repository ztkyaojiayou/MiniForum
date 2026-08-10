package com.example.miniforum.repository;

import com.example.miniforum.entity.User;
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

    public long count() {
        return storage.size();
    }
}
