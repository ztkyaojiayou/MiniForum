package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.SearchResultVO;
import com.tkzou.miniforum.service.SearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;

/**
 * 综合搜索接口（帖子 + 用户）
 * <p>
 * 需登录（由 AuthInterceptor 拦截 /api/search/**）。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    /** 综合搜索：返回命中的帖子与用户 */
    @GetMapping
    public ResponseEntity<Result<SearchResultVO>> search(@RequestParam String keyword,
                                                         HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(searchService.search(keyword, username)));
    }
}
