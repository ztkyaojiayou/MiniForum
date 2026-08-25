package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.dto.UserBriefVO;
import com.tkzou.miniforum.service.FollowService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

/**
 * 关注 / 粉丝接口
 * <p>
 * 全部需要登录（由 AuthInterceptor 拦截 /api/follows/**）。
 */
@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /** 关注某用户 */
    @PostMapping("/{userId}")
    public ResponseEntity<Result<Void>> follow(@PathVariable Long userId,
                                               HttpSession session) {
        Long me = (Long) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");
        followService.follow(me, userId, username);
        return ResponseEntity.ok(Result.success("关注成功", null));
    }

    /** 取消关注 */
    @DeleteMapping("/{userId}")
    public ResponseEntity<Result<Void>> unfollow(@PathVariable Long userId,
                                                 HttpSession session) {
        Long me = (Long) session.getAttribute("userId");
        followService.unfollow(me, userId);
        return ResponseEntity.ok(Result.success("已取消关注", null));
    }

    /** 是否已关注某用户 */
    @GetMapping("/status/{userId}")
    public ResponseEntity<Result<Map<String, Boolean>>> isFollowing(@PathVariable Long userId,
                                                                    HttpSession session) {
        Long me = (Long) session.getAttribute("userId");
        boolean following = followService.isFollowing(me, userId);
        return ResponseEntity.ok(Result.success(Map.of("following", following)));
    }

    /** 我关注的人列表（默认查自己，可用 userId 查他人） */
    @GetMapping("/following")
    public ResponseEntity<Result<List<UserBriefVO>>> getFollowing(
            @RequestParam(required = false) Long userId,
            HttpSession session) {
        Long me = userId != null ? userId : (Long) session.getAttribute("userId");
        return ResponseEntity.ok(Result.success(followService.getFollowing(me)));
    }

    /** 我的粉丝列表（默认查自己，可用 userId 查他人） */
    @GetMapping("/followers")
    public ResponseEntity<Result<List<UserBriefVO>>> getFollowers(
            @RequestParam(required = false) Long userId,
            HttpSession session) {
        Long me = userId != null ? userId : (Long) session.getAttribute("userId");
        return ResponseEntity.ok(Result.success(followService.getFollowers(me)));
    }

    /** 关注流：我关注的人发布的帖子（分页，最新在前） */
    @GetMapping("/feed")
    public ResponseEntity<Result<PageResult<PostVO>>> getFollowFeed(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpSession session) {
        Long me = (Long) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(followService.getFollowFeed(me, page, size, username)));
    }
}
