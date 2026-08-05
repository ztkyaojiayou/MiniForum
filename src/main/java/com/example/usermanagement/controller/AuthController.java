package com.example.usermanagement.controller;

import com.example.usermanagement.entity.User;
import com.example.usermanagement.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Map;

/**
 * 登录认证接口
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                     HttpSession session) {
        User user = authService.login(request.getUsername(), request.getPassword());
        session.setAttribute("userId", user.getId());
        session.setAttribute("username", user.getUsername());
        return ResponseEntity.ok(Map.of(
                "message", "登录成功",
                "username", user.getUsername()
        ));
    }

    /** 登出 */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Map.of("message", "已退出登录"));
    }

    /** 当前登录用户 */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(HttpSession session) {
        Object username = session.getAttribute("username");
        if (username == null) {
            return ResponseEntity.status(401).body(Map.of("message", "未登录"));
        }
        return ResponseEntity.ok(Map.of(
                "username", username
        ));
    }
}
