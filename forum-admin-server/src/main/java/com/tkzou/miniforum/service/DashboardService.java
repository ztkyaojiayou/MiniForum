package com.tkzou.miniforum.service;

import com.tkzou.miniforum.entity.Comment;
import com.tkzou.miniforum.entity.Like;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据看板服务
 * <p>
 * 聚合系统核心指标：用户数、帖子数、评论数、点赞数、今日新增等。
 */
@Service
public class DashboardService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final LikeRepository likeRepository;

    public DashboardService(UserRepository userRepository,
                            PostRepository postRepository,
                            CommentRepository commentRepository,
                            LikeRepository likeRepository) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.likeRepository = likeRepository;
    }

    /** 系统统计总览 */
    public Map<String, Object> getStats() {
        LocalDate today = LocalDate.now();
        long totalPosts = postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()))
                .count();
        long todayPosts = postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()))
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().toLocalDate().equals(today))
                .count();
        long todayComments = commentRepository.findAll().stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().toLocalDate().equals(today))
                .count();
        long totalLikes = likeRepository.findAll().size();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalPosts", totalPosts);
        stats.put("totalComments", commentRepository.count());
        stats.put("totalLikes", totalLikes);
        stats.put("todayPosts", todayPosts);
        stats.put("todayComments", todayComments);
        stats.put("draftPosts", postRepository.findAll().stream()
                .filter(p -> Post.STATUS_DRAFT.equals(p.getStatus()))
                .count());
        return stats;
    }
}
