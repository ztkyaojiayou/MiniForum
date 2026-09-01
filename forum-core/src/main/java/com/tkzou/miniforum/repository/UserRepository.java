package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.User;
import java.util.List;
import java.util.Optional;

/**
 * 用户仓库接口
 * <p>
 * 双实现：内存 {@link InMemoryUserRepository}（@Profile("!prod")，演示）/
 * MySQL {@code MySqlUserRepository}（@Profile("prod")，demo-runner/src/prod，行级表 users）。
 */
public interface UserRepository {

    User save(User user);

    Optional<User> findById(Long id);

    /**
     * 批量按 ID 查用户（P2-25 N+1 修复）：FollowService 等批量回源，避免逐条 findById。
     * 返回存在的用户（ID 顺序不保证），空集合返回空列表。
     */
    List<User> findByIds(java.util.Collection<Long> ids);

    Optional<User> findByUsername(String username);

    List<User> findAll();

    void deleteById(Long id);

    boolean existsById(Long id);

    List<User> exportAll();

    void importAll(List<User> users);

    long count();
}