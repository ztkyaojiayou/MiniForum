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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;

/**
 * 评论接口（挂 /api/posts/{postId}/comments + /api/comments）
 * <p>
 * 发表/查看（热度/时间排序）/删除评论、楼中楼回复（parentId）、评论点赞。
 * 写操作（发表/删除/点赞）需登录；游客可浏览评论。
 */
@RestController
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /** 查看某帖子的评论列表（sort=heat 按热度，默认按时间最新在前） */
    @GetMapping("/api/posts/{postId}/comments")
    public ResponseEntity<Result<List<CommentVO>>> list(@PathVariable Long postId,
                                                        @RequestParam(required = false, defaultValue = "time") String sort,
                                                        HttpSession session) {
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(commentService.getComments(postId, username, sort)));
    }

    /** 发表评论 / 楼中楼回复（parentId 非空则为回复） */
    @PostMapping("/api/posts/{postId}/comments")
    public ResponseEntity<Result<CommentVO>> create(@PathVariable Long postId,
                                                    @Valid @RequestBody CommentCreateDTO dto,
                                                    HttpSession session) {
        String username = (String) session.getAttribute("username");
        Long userId = (Long) session.getAttribute("userId");
        CommentVO created = commentService.addComment(postId, dto, username, userId, dto.getParentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success("评论成功", created));
    }

    /** 评论点赞 */
    @PostMapping("/api/comments/{commentId}/like")
    public ResponseEntity<Result<CommentVO>> like(@PathVariable Long commentId) {
        return ResponseEntity.ok(Result.success("已点赞", commentService.likeComment(commentId)));
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
