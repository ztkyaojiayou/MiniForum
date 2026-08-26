package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据看板接口（/api/dashboard，需登录，AuthInterceptor 拦截 /api/dashboard/**）
 * <p>
 * 系统统计：用户/帖子/评论/点赞数 + 今日新增，供运营看板与首页角标。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    /** 系统统计总览 */
    @GetMapping("/stats")
    public ResponseEntity<Result<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(Result.success(dashboardService.getStats()));
    }
}
