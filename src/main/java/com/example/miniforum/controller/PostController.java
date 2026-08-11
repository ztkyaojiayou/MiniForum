package com.example.miniforum.controller;

import com.example.miniforum.common.Result;
import com.example.miniforum.dto.PageResult;
import com.example.miniforum.dto.PostCreateDTO;
import com.example.miniforum.dto.PostVO;
import com.example.miniforum.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ResponseEntity<Result<PostVO>> createPost(@Valid @RequestBody PostCreateDTO dto,
                                                     HttpSession session) {
        String author = (String) session.getAttribute("username");
        PostVO created = postService.createPost(dto, author);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success("发帖成功", created));
    }

    /** 查看所有帖子（支持分页与按标签筛选） */
    @GetMapping
    public ResponseEntity<Result<?>> getAllPosts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String tag,
            HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (page == null && size == null) {
            return ResponseEntity.ok(Result.success(postService.getAllPosts(username)));
        }
        int p = page == null ? 1 : page;
        int s = size == null ? 10 : size;
        return ResponseEntity.ok(Result.success(postService.getPosts(p, s, tag, username)));
    }

    /** 关键字搜索（标题命中优先于内容命中） */
    @GetMapping("/search")
    public ResponseEntity<Result<List<PostVO>>> search(@RequestParam String keyword,
                                                       HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(postService.search(keyword, username)));
    }

    /** 查看帖子详情 */
    @GetMapping("/{id}")
    public ResponseEntity<Result<PostVO>> getPostById(@PathVariable Long id,
                                                      HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(postService.getById(id, username)));
    }

    /** 点赞 */
    @PostMapping("/{id}/like")
    public ResponseEntity<Result<PostVO>> like(@PathVariable Long id,
                                               HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success("点赞成功", postService.like(id, username)));
    }

    /** 取消点赞 */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Result<PostVO>> unlike(@PathVariable Long id,
                                                 HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success("已取消点赞", postService.unlike(id, username)));
    }
}
