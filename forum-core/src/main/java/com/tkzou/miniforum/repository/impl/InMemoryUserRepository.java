package com.tkzou.miniforum.repository.impl;
import com.tkzou.miniforum.repository.UserRepository;

import com.tkzou.miniforum.entity.User;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import com.tkzou.miniforum.util.EntityIdProvider;
import com.tkzou.miniforum.util.IdProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 内存存储的用户仓库
 * 使用 ConcurrentHashMap 保证线程安全
 */
@Repository
@Profile("!prod")
public class InMemoryUserRepository implements UserRepository {
    /** ID 生成器（构造器注入，P2-26）：Spring 按 profile 注入 EntityIdProvider(!prod) / SnowflakeIdProvider(prod)；测试直构走无参默认 */
    private final IdProvider idProvider;

    /** 测试/默认构造：EntityIdProvider（演示默认） */
    public InMemoryUserRepository() {
        this(new EntityIdProvider());
    }

    /** 构造器注入：避免 @Autowired(required=false) 字段注入掩盖注入失败 */
    @Autowired
    public InMemoryUserRepository(IdProvider idProvider) {
        this.idProvider = idProvider;
    }


    private final Map<Long, User> storage = new ConcurrentHashMap<>();

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            user.setId(idProvider.next("User"));
        }
        storage.put(user.getId(), user);
        return user;
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(storage.get(id));
    }

    @Override
    public List<User> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> idSet = new HashSet<>(ids);
        return storage.values().stream()
                .filter(u -> u.getId() != null && idSet.contains(u.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return storage.values().stream()
                .filter(u -> u.getUsername().equals(username))
                .findFirst();
    }

    @Override
    public List<User> findAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(Long id) {
        storage.remove(id);
    }

    @Override
    public boolean existsById(Long id) {
        return storage.containsKey(id);
    }

    /** 导出全部用户（用于持久化，按 ID 升序） */
    @Override
    public List<User> exportAll() {
        return storage.values().stream()
                .sorted((a, b) -> Long.compare(a.getId(), b.getId()))
                .collect(Collectors.toList());
    }

    /** 清空并批量导入（用于从持久化数据恢复） */
    @Override
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

    @Override
    public long count() {
        return storage.size();
    }
}
