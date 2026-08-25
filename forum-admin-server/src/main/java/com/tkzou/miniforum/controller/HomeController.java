package com.tkzou.miniforum.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpSession;

/**
 * 根路径控制器：
 * 访问 http://localhost:8090 时：
 *   - 已登录 -> 跳转到首页 index.html
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

    /** 分类页独立路由：/category/{key} 转发到首页，前端根据路径自动激活对应分类（可分享/收藏，刷新不丢状态） */
    @GetMapping("/category/{key}")
    public String categoryPage(@PathVariable String key) {
        return "forward:/index.html";
    }
}
