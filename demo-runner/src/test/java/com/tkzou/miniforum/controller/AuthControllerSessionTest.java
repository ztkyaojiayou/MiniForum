package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.util.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Session 认证契约测试（P2-4）
 * <p>
 * 演示上下文（@SpringBootTest，零中间件）下守护"Controller 用 HttpSession 的契约"：
 * login 写 session → me 读回 → logout 失效后未登录返回 401。
 * 生产（@Profile("prod")）Spring Session Redis 透明替换 HttpSession，此契约保持不变。
 */
@SpringBootTest(properties = "app.persistence.enabled=false")
@AutoConfigureMockMvc
class AuthControllerSessionTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setUsername("sessionuser");
        user.setPassword(PasswordEncoder.encode("test123"));
        userRepository.save(user);
    }

    @Test
    void login_thenMe_returnsUser_andLogoutInvalidates() throws Exception {
        // 登录：服务端创建 session（MockMvc 不回写 cookie，直接取请求侧 session）
        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sessionuser\",\"password\":\"test123\"}"))
                .andExpect(status().isOk())
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
        assertNotNull(session, "登录应创建服务端 session");

        // 带 session 查 me → 200 + 用户名
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("sessionuser"));

        // 登出 → session 失效
        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isOk());

        // 失效后（等价于新请求无 session）再查 me → 401
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
