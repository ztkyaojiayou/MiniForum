package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.PostCreateDTO;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.service.PostService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 帖子接口（/api/posts）
 * <p>
 * 发帖/列表（分页+标签/分类/话题筛选）/详情/搜索/热门/我的/草稿/编辑/删除/回收站/点赞/转发。
 * 纯内存存储（可 JSON 持久化），不依赖任何第三方中间件。
 * 写操作需登录（发帖/点赞/转发/删除等，AuthInterceptor 拦截 /api/posts/**）；游客可浏览读路径（列表/详情/热门/搜索）。
 * 发帖入口 → {@code PostService.createPost} → 事件发布（PostCreatedNotifier，见 service 包）。
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /** 发帖（body 中 publish=false 时存为草稿） */
    @PostMapping
    public ResponseEntity<Result<PostVO>> createPost(@Valid @RequestBody PostCreateDTO dto,
                                                     HttpSession session) {
        String author = (String) session.getAttribute("username");
        Long authorId = (Long) session.getAttribute("userId");
        PostVO created = postService.createPost(dto, author, authorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success("发帖成功", created));
    }

    /** 查看所有已发布帖子（支持分页、按标签/分类/话题筛选、status=DRAFT 查看自己的草稿） */
    @GetMapping
    public ResponseEntity<Result<?>> getAllPosts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String status,
            HttpSession session) {
        String username = (String) session.getAttribute("username");
        // 查看我的草稿：GET /api/posts?status=DRAFT
        if ("DRAFT".equalsIgnoreCase(status)) {
            int p = page == null ? 1 : page;
            int s = size == null ? 10 : size;
            return ResponseEntity.ok(Result.success(postService.getMyPosts(username, "DRAFT", p, s)));
        }
        if (topic != null && !topic.isBlank()) {
            // 话题聚合：返回 PageResult 结构，与前端 feed 分页解析一致
            List<PostVO> list = postService.getPostsByTopic(topic.trim(), username);
            PageResult<PostVO> pr = new PageResult<>(list, list.size(), 1, Math.max(1, list.size()));
            return ResponseEntity.ok(Result.success(pr));
        }
        if (page == null && size == null) {
            return ResponseEntity.ok(Result.success(postService.getAllPosts(username)));
        }
        int p = page == null ? 1 : page;
        int s = size == null ? 10 : size;
        return ResponseEntity.ok(Result.success(postService.getPosts(p, s, tag, category, username)));
    }

    /** 我的文章（当前用户全部帖子，可按 status=DRAFT/PUBLISHED 过滤，分页） */
    @GetMapping("/my")
    public ResponseEntity<Result<PageResult<PostVO>>> getMyPosts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(postService.getMyPosts(username, status, page, size)));
    }

    /** 关键字搜索（标题命中优先于内容命中） */
    @GetMapping("/search")
    public ResponseEntity<Result<List<PostVO>>> search(@RequestParam String keyword,
                                                       HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(postService.search(keyword, username)));
    }

    /** 热门排行（按阅读量降序） */
    @GetMapping("/hot")
    public ResponseEntity<Result<List<PostVO>>> getHotPosts(
            @RequestParam(required = false, defaultValue = "10") Integer limit,
            HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(postService.getHotPosts(limit, username)));
    }

    /** 查看帖子详情（草稿仅作者本人/管理员可见） */
    @GetMapping("/{id}")
    public ResponseEntity<Result<PostVO>> getPostById(@PathVariable Long id,
                                                      HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(postService.getById(id, username)));
    }

    /** 更新帖子（仅作者本人/管理员，body 中 publish=true 发布，false 存草稿） */
    @PutMapping("/{id}")
    public ResponseEntity<Result<PostVO>> updatePost(@PathVariable Long id,
                                                     @Valid @RequestBody PostCreateDTO dto,
                                                     HttpSession session) {
        String username = (String) session.getAttribute("username");
        PostVO updated = postService.updatePost(id, dto, username, dto.getPublish());
        return ResponseEntity.ok(Result.success(dto.getPublish() ? "已更新并发布" : "已保存为草稿", updated));
    }

    /** 删除帖子（软删除，移入回收站；仅作者本人/管理员） */
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deletePost(@PathVariable Long id,
                                                   HttpSession session) {
        String username = (String) session.getAttribute("username");
        postService.deletePost(id, username);
        return ResponseEntity.ok(Result.success("帖子已移入回收站", null));
    }

    /** 恢复回收站中的帖子（仅作者本人/管理员） */
    @PostMapping("/{id}/restore")
    public ResponseEntity<Result<PostVO>> restorePost(@PathVariable Long id,
                                                      HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success("已恢复", postService.restorePost(id, username)));
    }

    /** 我的回收站（当前用户已删除的帖子，分页） */
    @GetMapping("/recycle")
    public ResponseEntity<Result<PageResult<PostVO>>> getRecycleBin(
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(postService.getRecycleBin(username, page, size)));
    }

    /** 点赞 */
    @PostMapping("/{id}/like")
    public ResponseEntity<Result<PostVO>> like(@PathVariable Long id,
                                               HttpSession session) {
        String username = (String) session.getAttribute("username");
        Long userId = (Long) session.getAttribute("userId");
        return ResponseEntity.ok(Result.success("点赞成功", postService.like(id, username, userId)));
    }

    /** 取消点赞 */
    @DeleteMapping("/{id}/like")
    public ResponseEntity<Result<PostVO>> unlike(@PathVariable Long id,
                                                 HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success("已取消点赞", postService.unlike(id, username)));
    }

    /** 转发帖子（body: {"content": "转发评语"}） */
    @PostMapping("/{id}/repost")
    public ResponseEntity<Result<PostVO>> repost(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, String> body,
                                                 HttpSession session) {
        String username = (String) session.getAttribute("username");
        Long userId = (Long) session.getAttribute("userId");
        String comment = body == null ? null : body.get("content");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Result.success("转发成功", postService.repost(id, comment, username, userId)));
    }
}
