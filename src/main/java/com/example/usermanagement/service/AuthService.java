package com.example.usermanagement.service;

import com.example.usermanagement.entity.User;
import com.example.usermanagement.exception.InvalidCredentialsException;
import com.example.usermanagement.repository.UserRepository;
import com.example.usermanagement.util.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * 登录认证服务
 */
@Service
public class AuthService {

    /** 默认管理员账号 */
    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    /** 默认管理员密码 */
    private static final String DEFAULT_ADMIN_PASSWORD = "admin123";
    /** 默认管理员邮箱 */
    private static final String DEFAULT_ADMIN_EMAIL = "admin@example.com";
    /** 默认管理员年龄 */
    private static final int DEFAULT_ADMIN_AGE = 30;

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 初始化默认管理员账号
     */
    @PostConstruct
    public void initDefaultAdmin() {
        if (userRepository.findByUsername(DEFAULT_ADMIN_USERNAME).isEmpty()) {
            User admin = new User();
            admin.setUsername(DEFAULT_ADMIN_USERNAME);
            admin.setPassword(PasswordEncoder.encode(DEFAULT_ADMIN_PASSWORD));
            admin.setEmail(DEFAULT_ADMIN_EMAIL);
            admin.setAge(DEFAULT_ADMIN_AGE);
            userRepository.save(admin);
        }
    }

    /**
     * 登录校验：用户名 + 密码
     *
     * @return 登录成功的用户
     * @throws InvalidCredentialsException 用户名或密码错误
     */
    public User login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty() || !PasswordEncoder.matches(password, userOpt.get().getPassword())) {
            throw new InvalidCredentialsException("用户名或密码错误");
        }
        return userOpt.get();
    }
}
