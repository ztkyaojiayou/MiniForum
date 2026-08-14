package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.CommentCreateDTO;
import com.tkzou.miniforum.dto.CommentVO;
import com.tkzou.miniforum.entity.Comment;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          NotificationService notificationService,
                          UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    /** 查看某帖子的评论列表（时间正序，附带当前用户是否为作者） */
    public List<CommentVO> getComments(Long postId, String username) {
        ensurePostExists(postId);
        return commentRepository.findByPostId(postId).stream()
                .map(c -> new CommentVO(c, c.getAuthor().equals(username)))
                .collect(Collectors.toList());
    }

    /** 发表评论（评论后通知帖子作者） */
    public CommentVO addComment(Long postId, CommentCreateDTO dto, String username, Long actorId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在：id=" + postId));
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthor(username);
        comment.setContent(dto.getContent().trim());
        comment.setCreatedAt(LocalDateTime.now());
        Comment saved = commentRepository.save(comment);
        // 通知帖子作者（评论自己的帖子不通知）
        notificationService.notify(post.getAuthorId(), actorId, username,
                Notification.TYPE_COMMENT, postId, "评论了你的帖子《" + post.getTitle() + "》");
        // @提及通知：评论中被 @ 的用户
        notifyMentionsInComment(post, saved, username, actorId);
        return new CommentVO(saved, true);
    }

    /** @提及 识别正则：匹配 @用户名（中英文、数字、下划线，1~20 字符） */
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w\\u4e00-\\u9fa5]{1,20})");

    /** 评论中被 @ 的用户触发 MENTION 通知（用户不存在时静默忽略；@ 自己由 NotificationService 去重） */
    private void notifyMentionsInComment(Post post, Comment comment, String actorUsername, Long actorId) {
        if (comment.getContent() == null || comment.getContent().isBlank()) {
            return;
        }
        Set<String> mentions = new LinkedHashSet<>();
        Matcher m = MENTION_PATTERN.matcher(comment.getContent());
        while (m.find()) {
            mentions.add(m.group(1));
        }
        String title = post.getTitle() == null ? "帖子" : post.getTitle();
        for (String mentionName : mentions) {
            userRepository.findByUsername(mentionName).ifPresent(u ->
                    notificationService.notify(u.getId(), actorId, actorUsername,
                            Notification.TYPE_MENTION, post.getId(), "在《" + title + "》的评论中提到了你"));
        }
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
