package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.request.CommentCreateDTO;
import com.tkzou.miniforum.dto.response.CommentVO;
import com.tkzou.miniforum.entity.Comment;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorScene;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
    private final BehaviorLogger behaviorLogger;

    public CommentService(CommentRepository commentRepository,
                          PostRepository postRepository,
                          NotificationService notificationService,
                          UserRepository userRepository,
                          BehaviorLogger behaviorLogger) {
        this.commentRepository = commentRepository;
        this.postRepository = postRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.behaviorLogger = behaviorLogger;
    }

    /** 查看某帖子的评论列表（根评论 + 楼中楼回复；sort=heat 按热度，否则按时间最新在前） */
    public List<CommentVO> getComments(Long postId, String username, String sort) {
        ensurePostExists(postId);
        List<Comment> all = commentRepository.findByPostId(postId);
        Map<Long, List<Comment>> repliesByParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(Comment::getParentId));
        Comparator<Comment> rootOrder = "heat".equalsIgnoreCase(sort)
                ? Comparator.comparingLong(Comment::getLikeCount).reversed()
                        .thenComparing(Comparator.comparing(Comment::getCreatedAt).reversed())
                : Comparator.comparing(Comment::getCreatedAt).reversed();
        return all.stream()
                .filter(c -> c.getParentId() == null)
                .sorted(rootOrder)
                .map(c -> toVO(c, username, repliesByParent.getOrDefault(c.getId(), List.of())))
                .collect(Collectors.toList());
    }

    /** 组装 VO：根评论附带楼中楼回复列表 */
    private CommentVO toVO(Comment c, String username, List<Comment> replies) {
        CommentVO vo = new CommentVO(c, c.getAuthor().equals(username));
        List<CommentVO> replyVOs = replies.stream()
                .sorted(Comparator.comparing(Comment::getCreatedAt))
                .map(r -> toVO(r, username, List.of()))
                .collect(Collectors.toList());
        vo.setReplies(replyVOs);
        vo.setReplyCount(replyVOs.size());
        return vo;
    }

    /** 发表评论（支持楼中楼回复 parentId；评论后通知帖子作者，回复再通知被回复者） */
    public CommentVO addComment(Long postId, CommentCreateDTO dto, String username, Long actorId, Long parentId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在：id=" + postId));
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setAuthor(username);
        comment.setContent(dto.getContent().trim());
        comment.setCreatedAt(LocalDateTime.now());
        comment.setLikeCount(0);
        if (parentId != null) {
            Comment parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new ResourceNotFoundException("回复的评论不存在：id=" + parentId));
            if (!postId.equals(parent.getPostId())) {
                throw new BusinessException("回复的评论不属于该帖子");
            }
            comment.setParentId(parentId);
        }
        Comment saved = commentRepository.save(comment);
        behaviorLogger.log(actorId, postId, BehaviorType.COMMENT, BehaviorScene.POST, null);
        // 通知帖子作者（评论自己的帖子不通知）
        notificationService.notify(post.getAuthorId(), actorId, username,
                Notification.TYPE_COMMENT, postId, "评论了你的帖子《" + post.getTitle() + "》");
        // 若是楼中楼回复，也通知被回复的评论作者
        if (parentId != null) {
            commentRepository.findById(parentId).ifPresent(parent -> {
                if (!parent.getAuthor().equals(username)) {
                    userRepository.findByUsername(parent.getAuthor()).ifPresent(u ->
                            notificationService.notify(u.getId(), actorId, username,
                                    Notification.TYPE_MENTION, postId, "回复了你的评论"));
                }
            });
        }
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

    /**
     * 评论点赞（简化实现：不校验去重，直接 +1）。
     * 刻意取舍：评论赞低价值，只维护聚合快照 count，无"谁赞了"记录/不去重/无取消
     * （与 Post 点赞的 Like 表不对称，见 {@code Comment.likeCount} 字段注释）。
     */
    public CommentVO likeComment(Long commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("评论不存在：id=" + commentId));
        comment.setLikeCount(comment.getLikeCount() + 1);
        commentRepository.save(comment);
        return new CommentVO(comment, false);
    }

    /** 删除评论：作者本人或管理员可删除（级联删除楼中楼回复） */
    public void deleteComment(Long commentId, String username) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("评论不存在：id=" + commentId));
        boolean isOwner = comment.getAuthor().equals(username);
        boolean isAdmin = ADMIN_USERNAME.equals(username);
        if (!isOwner && !isAdmin) {
            throw new BusinessException("只能删除自己的评论");
        }
        // 级联删除该评论的楼中楼回复
        List<Comment> replies = commentRepository.findByPostId(comment.getPostId()).stream()
                .filter(c -> commentId.equals(c.getParentId()))
                .collect(Collectors.toList());
        for (Comment r : replies) {
            commentRepository.deleteById(r.getId());
        }
        commentRepository.deleteById(commentId);
    }

    private void ensurePostExists(Long postId) {
        if (postRepository.findById(postId).isEmpty()) {
            throw new ResourceNotFoundException("帖子不存在：id=" + postId);
        }
    }
}
