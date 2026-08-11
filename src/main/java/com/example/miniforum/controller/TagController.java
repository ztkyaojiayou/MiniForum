package com.example.miniforum.controller;

import com.example.miniforum.common.Result;
import com.example.miniforum.dto.TagInfo;
import com.example.miniforum.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签接口：返回所有标签及其帖子数
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
}
