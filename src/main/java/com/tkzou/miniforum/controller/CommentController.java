package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.CommentCreateDTO;
import com.tkzou.miniforum.dto.CommentVO;
import com.tkzou.miniforum.service.CommentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;

/**
 * 评论接口
 * <p>
 * 发表/查看评论挂在帖子下（/api/posts/{postId}/comments，受登录拦截），
 * 删除评论独立路径 /api/comments/{commentId}。
 */
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** 查看某帖子的评论列表 */
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<Result<List<CommentVO>>> list(@PathVariable Long postId,
                                                        HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(commentService.getComments(postId, username)));
    }

    /** 发表评论 */
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<Result<CommentVO>> create(@PathVariable Long postId,
                                                    @Valid @RequestBody CommentCreateDTO dto,
                                                    HttpSession session) {
        String username = (String) session.getAttribute("username");
        CommentVO created = commentService.addComment(postId, dto, username);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success("评论成功", created));
    }

    /** 删除评论（作者本人或管理员） */
    @DeleteMapping("/api/comments/{commentId}")
    public ResponseEntity<Result<Void>> delete(@PathVariable Long commentId,
                                               HttpSession session) {
        String username = (String) session.getAttribute("username");
        commentService.deleteComment(commentId, username);
        return ResponseEntity.ok(Result.success("评论已删除", null));
    }
}
