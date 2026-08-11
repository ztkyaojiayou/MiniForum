package com.example.miniforum.service;

import com.example.miniforum.dto.CommentCreateDTO;
import com.example.miniforum.dto.CommentVO;
import com.example.miniforum.entity.Comment;
import com.example.miniforum.exception.BusinessException;
import com.example.miniforum.exception.ResourceNotFoundException;
import com.example.miniforum.repository.CommentRepository;
import com.example.miniforum.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 评论服务
 */
@Service
public class CommentService {

    /** 管理员用户名（可删除任意评论） */
    private static final String ADMIN_USERNAME = "admin";

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;

    public CommentService(CommentRepository commentRepository, PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
    }

    /** 查看某帖子的评论列表（时间正序，附带当前用户是否为作者） */
    public List<CommentVO> getComments(Long postId, String username) {
        ensurePostExists(postId);
        return commentRepository.findByPostId(postId).stream()
                .map(c -> new CommentVO(c, c.getAuthor().equals(username)))
                .collect(Collectors.toList());
    }

    /** 发表评论 */
    public CommentVO addComment(Long postId, CommentCreateDTO dto, String username) {
        ensurePostExists(postId);
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthor(username);
        comment.setContent(dto.getContent().trim());
        comment.setCreatedAt(LocalDateTime.now());
        Comment saved = commentRepository.save(comment);
        return new CommentVO(saved, true);
    }

    /** 删除评论：作者本人或管理员可删除 */
    public void deleteComment(Long commentId, String username) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("评论不存在：id=" + commentId));
        boolean isOwner = comment.getAuthor().equals(username);
        boolean isAdmin = ADMIN_USERNAME.equals(username);
        if (!isOwner && !isAdmin) {
            throw new BusinessException("只能删除自己的评论");
        }
        commentRepository.deleteById(commentId);
    }

    private void ensurePostExists(Long postId) {
        if (postRepository.findById(postId).isEmpty()) {
            throw new ResourceNotFoundException("帖子不存在：id=" + postId);
        }
    }
}
