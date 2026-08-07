package com.example.usermanagement.controller;

import com.example.usermanagement.common.Result;
import com.example.usermanagement.dto.PostCreateDTO;
import com.example.usermanagement.entity.Post;
import com.example.usermanagement.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;

/**
 * 发帖 / 查看帖子接口
 * <p>
 * 纯内存存储，不依赖任何第三方中间件。
 * 发帖与查看均需登录（由 AuthInterceptor 拦截 /api/posts/**）。
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /** 发帖 */
    @PostMapping
    public ResponseEntity<Result<Post>> createPost(@Valid @RequestBody PostCreateDTO dto,
                                                   HttpSession session) {
        String author = (String) session.getAttribute("username");
        Post created = postService.createPost(dto, author);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success("发帖成功", created));
    }

    /** 查看所有帖子 */
    @GetMapping
    public ResponseEntity<Result<List<Post>>> getAllPosts() {
        return ResponseEntity.ok(Result.success(postService.getAllPosts()));
    }
}
