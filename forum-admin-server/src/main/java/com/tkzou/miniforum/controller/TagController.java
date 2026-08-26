package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.TagInfo;
import com.tkzou.miniforum.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签/话题接口（/api/tags，游客可看）
 * <p>
 * 标签列表（含帖子数）、话题榜（#话题#）、按标签/话题筛选帖子。
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final PostService postService;

    public TagController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping
    public ResponseEntity<Result<List<TagInfo>>> getTags() {
        return ResponseEntity.ok(Result.success(postService.getAllTags()));
    }

    /** 话题列表（内容中 #话题# 聚合，按帖子数降序） */
    @GetMapping("/topics")
    public ResponseEntity<Result<List<TagInfo>>> getTopics() {
        return ResponseEntity.ok(Result.success(postService.getAllTopics()));
    }
}
