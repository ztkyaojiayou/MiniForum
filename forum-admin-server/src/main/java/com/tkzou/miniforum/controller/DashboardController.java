package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 数据看板接口
 * <p>
 * 返回系统核心统计指标（需登录，由 AuthInterceptor 拦截 /api/dashboard/**）。
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
