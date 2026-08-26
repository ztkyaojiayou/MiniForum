package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.RecommendPostVO;
import com.tkzou.miniforum.dto.TrackRequest;
import com.tkzou.miniforum.exception.UnauthorizedException;
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
 * AuthInterceptor 对 GET /api/recommend/** 放行游客（详情页"相关推荐"游客可看），
 * 但 <b>个性化推荐流 feed 需登录</b>（内部以 userId 为 null 守卫，返回 401）。
 * <ul>
 *   <li>GET /feed → {@code RecommendService.recommend} 完整漏斗（召回→排序→重排→冷启动→下发），
 *       服务端自动记 EXPOSE，前端切"✨ 推荐"Tab 消费；<b>需登录</b>；</li>
 *   <li>GET /related → {@code RecommendService.related} 详情页 ItemCF 相似帖；游客可看；</li>
 *   <li>POST /track → 前端点击/负反馈打点（写操作，拦截器要求登录）。</li>
 * </ul>
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

    /** 个性化推荐流（需登录：个性化依赖 userId，游客/会话过期返回 401） */
    @GetMapping("/feed")
    public ResponseEntity<Result<PageResult<RecommendPostVO>>> feed(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");
        if (userId == null) {
            // AuthInterceptor 对 /api/recommend 的 GET 放行游客（related 详情页游客可看），
            // 但 feed 是强个性化接口，必须登录，否则下游画像/行为查询会收到 null userId
            throw new UnauthorizedException("请先登录");
        }
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

    /** 前端打点：点击/负反馈/阅读停留等行为上报 */
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
        // 阅读停留：带时长(≥1s)的 VIEW/DWELL → 记为 DWELL，时长进入行为日志（仿抖音"观看时长"信号）
        Double duration = request.getDurationSec();
        if (duration != null && duration < 1.0) {
            duration = null;
        }
        if (type == BehaviorType.VIEW && duration != null) {
            type = BehaviorType.DWELL;
        }
        behaviorLogger.log(userId, request.getPostId(), type, "TRACK", null, duration);
        return ResponseEntity.ok(Result.success("已记录", null));
    }
}
