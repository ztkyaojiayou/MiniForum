package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.common.PageResult;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.service.FavoriteService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * 收藏接口（/api/favorites，需登录）
 * <p>
 * 收藏/取消收藏某帖、我的收藏列表。收藏行为同时进入行为日志（推荐信号之一）。
 */
@RestController
@RequestMapping("/api/favorites")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    /** 收藏帖子 */
    @PostMapping("/{postId}")
    public ResponseEntity<Result<Void>> favorite(@PathVariable Long postId,
                                                 HttpSession session) {
        String username = (String) session.getAttribute("username");
        favoriteService.favorite(postId, username);
        return ResponseEntity.ok(Result.success("收藏成功", null));
    }

    /** 取消收藏 */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Result<Void>> unfavorite(@PathVariable Long postId,
                                                   HttpSession session) {
        String username = (String) session.getAttribute("username");
        favoriteService.unfavorite(postId, username);
        return ResponseEntity.ok(Result.success("已取消收藏", null));
    }

    /** 是否已收藏该帖子 */
    @GetMapping("/status/{postId}")
    public ResponseEntity<Result<Map<String, Boolean>>> isFavorite(@PathVariable Long postId,
                                                                   HttpSession session) {
        String username = (String) session.getAttribute("username");
        boolean favorited = favoriteService.isFavorite(postId, username);
        return ResponseEntity.ok(Result.success(Map.of("favorited", favorited)));
    }

    /** 我的收藏列表（分页，最新收藏在前） */
    @GetMapping("/my")
    public ResponseEntity<Result<PageResult<PostVO>>> getMyFavorites(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(favoriteService.getMyFavorites(username, page, size)));
    }
}
