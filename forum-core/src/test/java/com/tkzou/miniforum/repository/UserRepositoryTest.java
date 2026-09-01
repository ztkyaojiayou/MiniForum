package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.repository.impl.InMemoryUserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户仓库批量查询测试（P2-25 N+1 修复）：findByIds 返回存在用户、过滤不存在 id、空/null 输入返回空。
 */
class UserRepositoryTest {

    private final InMemoryUserRepository repo = new InMemoryUserRepository();

    private Long saveUser(String username) {
        User u = new User();
        u.setUsername(username);
        return repo.save(u).getId();
    }

    @Test
    void findByIds_shouldReturnExistingUsersOnly() {
        Long a = saveUser("alice");
        Long b = saveUser("bob");
        Long c = saveUser("carol");
        List<User> found = repo.findByIds(List.of(a, c, 999L)); // 含不存在的 id → 过滤
        assertEquals(2, found.size());
        assertTrue(found.stream().anyMatch(u -> u.getId().equals(a)));
        assertTrue(found.stream().anyMatch(u -> u.getId().equals(c)));
        assertTrue(found.stream().noneMatch(u -> u.getId().equals(b)), "未请求的 bob 不应返回");
    }

    @Test
    void findByIds_emptyOrNull_shouldReturnEmpty() {
        assertTrue(repo.findByIds(List.of()).isEmpty());
        assertTrue(repo.findByIds(null).isEmpty());
    }
}
