package com.example.usermanagement.service;

import com.example.usermanagement.entity.User;
import com.example.usermanagement.repository.UserRepository;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * 登录认证服务
 */
@Service
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 初始化默认管理员账号：admin / admin123
     */
    @PostConstruct
    public void initDefaultAdmin() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPassword("admin123");
            admin.setEmail("admin@example.com");
            admin.setAge(30);
            userRepository.save(admin);
        }
    }

    /**
     * 登录校验：用户名 + 密码
     * @return 登录成功的用户
     * @throws IllegalArgumentException 用户名或密码错误
     */
    public User login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty() || !userOpt.get().getPassword().equals(password)) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return userOpt.get();
    }
}
