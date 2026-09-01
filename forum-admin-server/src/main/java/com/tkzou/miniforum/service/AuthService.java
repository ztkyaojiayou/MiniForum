package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.response.UserVO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.InvalidCredentialsException;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.util.PasswordEncoder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Optional;

/**
 * 登录认证服务
 */
@Service
public class AuthService {

    /** 默认管理员用户名（可通过 app.admin.username 覆盖） */
    @Value("${app.admin.username:admin}")
    private String defaultAdminUsername;

    /**
     * 默认管理员密码（演示默认 admin123；生产 prod profile 下必须通过 app.admin.password 覆盖强口令，
     * 否则启动即失败，杜绝"生产初始密码固定"的安全隐患）
     */
    @Value("${app.admin.password:admin123}")
    private String defaultAdminPassword;

    /** 默认管理员邮箱（可通过 app.admin.email 覆盖） */
    @Value("${app.admin.email:admin@example.com}")
    private String defaultAdminEmail;

    /** 默认管理员年龄 */
    private static final int DEFAULT_ADMIN_AGE = 30;

    /** 已知的弱口令默认值：prod profile 下若命中此值则拒绝启动 */
    private static final String DEFAULT_WEAK_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final Environment environment;

    public AuthService(UserRepository userRepository, Environment environment) {
        this.userRepository = userRepository;
        this.environment = environment;
    }

    /**
     * 初始化默认管理员账号
     */
    @PostConstruct
    public void initDefaultAdmin() {
        if (environment.acceptsProfiles("prod") && DEFAULT_WEAK_PASSWORD.equals(defaultAdminPassword)) {
            throw new IllegalStateException("生产环境禁止使用默认口令 admin123，请通过配置 app.admin.password 设置强口令");
        }
        if (userRepository.findByUsername(defaultAdminUsername).isEmpty()) {
            User admin = new User();
            admin.setUsername(defaultAdminUsername);
            admin.setPassword(PasswordEncoder.encode(defaultAdminPassword));
            admin.setEmail(defaultAdminEmail);
            admin.setAge(DEFAULT_ADMIN_AGE);
            userRepository.save(admin);
        }
    }

    /**
     * 登录校验：用户名 + 密码
     *
     * @return 登录成功的用户视图（不含 password，避免密码散列越过服务边界）
     * @throws InvalidCredentialsException 用户名或密码错误
     */
    public UserVO login(String username, String password) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty() || !PasswordEncoder.matches(password, userOpt.get().getPassword())) {
            throw new InvalidCredentialsException("用户名或密码错误");
        }
        return new UserVO(userOpt.get());
    }
}
