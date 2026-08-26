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

    Optional<User> findByUsername(String username);

    List<User> findAll();

    void deleteById(Long id);

    boolean existsById(Long id);

    List<User> exportAll();

    void importAll(List<User> users);

    long count();
}