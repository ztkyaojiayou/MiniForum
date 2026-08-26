package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.UserCreateDTO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.service.AuthService;
import com.tkzou.miniforum.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

/**
 * 登录认证接口
 * <p>
 * /api/auth/** 为公开路径（WebConfig 不拦截），注册/登录均可匿名访问。
 */
/**
 * 认证接口（/api/auth，公开）
 * <p>
 * 注册（自动登录）/ 登录（写 Session）/ 退出 / 当前用户查询。
 * Session 认证：登录成功把 userId/username 写入 session，供 AuthInterceptor 与各 Controller 读取。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /** 登录请求体 */
    public static class LoginRequest {
        @NotBlank(message = "用户名不能为空")
        private String username;

        @NotBlank(message = "密码不能为空")
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    /** 登录 */
    @PostMapping("/login")
    public ResponseEntity<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest request,
                                                       HttpSession session) {
        User user = authService.login(request.getUsername(), request.getPassword());
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        LoginResponse data = new LoginResponse(user);
        return ResponseEntity.ok(Result.success("登录成功", data));
    }

    /** 注册（校验见 UserCreateDTO，注册成功后自动登录） */
    @PostMapping("/register")
    public ResponseEntity<Result<LoginResponse>> register(@Valid @RequestBody UserCreateDTO dto,
                                                          HttpSession session) {
        User user = userService.createUser(dto);
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        return ResponseEntity.ok(Result.success("注册成功", new LoginResponse(user)));
    }

    /** 登出 */
    @PostMapping("/logout")
    public ResponseEntity<Result<Void>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Result.success("已退出登录", null));
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public ResponseEntity<Result<LoginResponse>> me(HttpSession session) {
        Object username = session.getAttribute("username");
        if (username == null) {
            return ResponseEntity.status(401).body(Result.error(401, "未登录"));
        }
        Long userId = (Long) session.getAttribute("userId");
        return ResponseEntity.ok(Result.success(new LoginResponse((String) username, userId)));
    }

    /** 登录成功响应体 */
    public static class LoginResponse {
        private final String username;
        private final Long id;
        private final String nickname;
        private final String avatar;

        public LoginResponse(User user) {
            this.username = user.getUsername();
            this.id = user.getId();
            this.nickname = user.getNickname();
            this.avatar = user.getAvatar();
        }

        public LoginResponse(String username, Long id) {
            this.username = username;
            this.id = id;
            this.nickname = null;
            this.avatar = null;
        }

        public String getUsername() {
            return username;
        }

        public Long getId() {
            return id;
        }

        public String getNickname() {
            return nickname;
        }

        public String getAvatar() {
            return avatar;
        }
    }
}
