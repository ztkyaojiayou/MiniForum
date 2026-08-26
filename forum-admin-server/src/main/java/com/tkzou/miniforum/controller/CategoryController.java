package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.CategoryInfo;
import com.tkzou.miniforum.service.PostService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类接口（/api/categories，游客可看）
 * <p>
 * 返回固定 12 类 + 各分类已发布帖子数（"全部动态"虚拟分类置顶），供左栏分类导航。
 */
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final PostService postService;

    public CategoryController(PostService postService) {
        this.postService = postService;
    }

    /** 获取全部固定分类及各分类已发布帖子数（含"全部动态"虚拟分类） */
    @GetMapping
    public ResponseEntity<Result<List<CategoryInfo>>> getCategories() {
        return ResponseEntity.ok(Result.success(postService.getAllCategories()));
    }
}
