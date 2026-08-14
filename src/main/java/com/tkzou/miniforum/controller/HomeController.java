package com.tkzou.miniforum.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpSession;

/**
 * 根路径控制器：
 * 访问 http://localhost:8080 时：
 *   - 已登录 -> 跳转到用户管理页 index.html
 *   - 未登录 -> 跳转到登录页 login.html
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String home(HttpSession session) {
        Object username = session.getAttribute("username");
        if (username == null) {
            return "redirect:/login.html";
        }
        return "redirect:/index.html";
    }
}
