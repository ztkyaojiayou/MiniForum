package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.response.UserVO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.InvalidCredentialsException;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.repository.impl.InMemoryUserRepository;
import com.tkzou.miniforum.util.PasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 登录认证服务单元测试
 * <p>
 * 项 8：默认口令配置化——prod profile 下仍为弱口令必须启动失败（fail-fast）；
 * 项 1：login 返回脱敏 UserVO（不含 password），密码只存于存储层。
 */
class AuthServiceTest {

    private UserRepository userRepository;

    /** 构造 AuthService（模拟 @Value 注入的默认配置；prod=true 时激活 prod profile） */
    private AuthService newAuthService(boolean prod) {
        userRepository = new InMemoryUserRepository();
        StandardEnvironment env = new StandardEnvironment();
        if (prod) {
            env.setActiveProfiles("prod");
        }
        AuthService authService = new AuthService(userRepository, env);
        ReflectionTestUtils.setField(authService, "defaultAdminUsername", "admin");
        ReflectionTestUtils.setField(authService, "defaultAdminPassword", "admin123");
        ReflectionTestUtils.setField(authService, "defaultAdminEmail", "admin@example.com");
        return authService;
    }

    @Test
    void initDefaultAdmin_prodWithDefaultPassword_shouldFailFast() {
        AuthService authService = newAuthService(true);
        assertThrows(IllegalStateException.class, authService::initDefaultAdmin,
                "生产环境禁止默认口令，应启动即失败");
    }

    @Test
    void initDefaultAdmin_prodWithStrongPassword_shouldCreateAdmin() {
        AuthService authService = newAuthService(true);
        ReflectionTestUtils.setField(authService, "defaultAdminPassword", "S3curePass!2026");
        authService.initDefaultAdmin();
        assertTrue(userRepository.findByUsername("admin").isPresent(),
                "生产配置强口令后应正常创建管理员");
    }

    @Test
    void initDefaultAdmin_defaultProfile_shouldCreateAdminWithDefaultPassword() {
        AuthService authService = newAuthService(false);
        authService.initDefaultAdmin();
        User admin = userRepository.findByUsername("admin").orElseThrow();
        assertTrue(PasswordEncoder.matches("admin123", admin.getPassword()),
                "演示 profile 保留开箱即用的 admin/admin123");
    }

    @Test
    void login_shouldReturnUserVOWithoutPassword() {
        AuthService authService = newAuthService(false);
        authService.initDefaultAdmin();

        UserVO vo = authService.login("admin", "admin123");
        assertEquals("admin", vo.getUsername());
        assertEquals("admin@example.com", vo.getEmail());
        // 密码只存在于存储层，服务返回的 VO 不含 password
        User stored = userRepository.findByUsername("admin").orElseThrow();
        assertTrue(PasswordEncoder.matches("admin123", stored.getPassword()));
    }

    @Test
    void login_invalidCredentials_shouldThrow() {
        AuthService authService = newAuthService(false);
        authService.initDefaultAdmin();
        assertThrows(InvalidCredentialsException.class, () -> authService.login("admin", "wrong-pass"));
    }
}
