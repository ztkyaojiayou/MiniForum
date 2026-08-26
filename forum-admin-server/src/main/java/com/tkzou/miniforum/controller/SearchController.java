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
 * 综合搜索接口（/api/search，游客可看）
 * <p>
 * 关键词同时搜帖子（标题/内容/标签/话题）与用户（用户名/昵称），
 * 搜索词计入热搜（SearchService 内部打点行为日志）。当前为实时全表扫（数据量小够用）。
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
