package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.HotSearchVO;
import com.tkzou.miniforum.service.HotSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 热搜接口（/api/hot，游客可看）
 * <p>
 * 热搜榜：按标签聚合热度（阅读×1 + 点赞×2 + 评论×3，近 30 天时间衰减），
 * 带 爆/沸/热/新 等级；另提供热门帖子排行（首页右栏）。
 */
@RestController
@RequestMapping("/api/hot")
public class HotSearchController {

    private final HotSearchService hotSearchService;

    public HotSearchController(HotSearchService hotSearchService) {
        this.hotSearchService = hotSearchService;
    }

    /** 热搜关键词榜（按热度降序，默认 Top10） */
    @GetMapping("/search")
    public ResponseEntity<Result<List<HotSearchVO>>> getHotSearches(
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ResponseEntity.ok(Result.success(hotSearchService.getHotSearches(limit)));
    }
}
