package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.RecommendPostVO;
import com.tkzou.miniforum.dto.TrackRequest;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.service.RecommendService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 推荐接口
 * <p>
 * 需登录（由 AuthInterceptor 拦截 /api/recommend/**）。
 * - GET /feed：个性化推荐流（含推荐理由，服务端自动记录曝光）
 * - GET /related：详情页相关推荐（ItemCF "看过这篇的人还看"）
 * - POST /track：前端点击/负反馈打点
 */
@RestController
@RequestMapping("/api/recommend")
public class RecommendController {

    private final RecommendService recommendService;
    private final BehaviorLogger behaviorLogger;

    public RecommendController(RecommendService recommendService, BehaviorLogger behaviorLogger) {
        this.recommendService = recommendService;
        this.behaviorLogger = behaviorLogger;
    }

    /** 个性化推荐流 */
    @GetMapping("/feed")
    public ResponseEntity<Result<PageResult<RecommendPostVO>>> feed(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 20);
        // 一次多取便于分页切片（每次请求重新计算，弱一致性，学习项目可接受）
        RecommendContext ctx = new RecommendContext(userId, "HOME", LocalDateTime.now(), safePage * safeSize);
        // AB 实验：行为日志携带 expId 便于离线归因；实验组 B 走多样性变体配置
        List<RecommendPostVO> all = recommendService.recommend(ctx, username, "rec-v1");
        int from = Math.min((safePage - 1) * safeSize, all.size());
        int to = Math.min(from + safeSize, all.size());
        List<RecommendPostVO> records = all.isEmpty() ? new ArrayList<>() : all.subList(from, to);
        return ResponseEntity.ok(Result.success(new PageResult<>(records, all.size(), safePage, safeSize)));
    }

    /** 详情页相关推荐 */
    @GetMapping("/related")
    public ResponseEntity<Result<List<RecommendPostVO>>> related(@RequestParam Long postId,
                                                                 HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(recommendService.related(postId, username)));
    }

    /** 前端打点：点击/负反馈（不感兴趣）等行为上报 */
    @PostMapping("/track")
    public ResponseEntity<Result<Void>> track(@RequestBody TrackRequest request,
                                              HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        BehaviorType type;
        try {
            type = BehaviorType.valueOf(request.getAction() == null ? "" : request.getAction().toUpperCase());
        } catch (IllegalArgumentException e) {
            type = BehaviorType.CLICK;
        }
        behaviorLogger.log(userId, request.getPostId(), type, "TRACK", null);
        return ResponseEntity.ok(Result.success("已记录", null));
    }
}
