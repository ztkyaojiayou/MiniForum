package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.entity.Favorite;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 收藏服务
 * <p>
 * 负责帖子的收藏 / 取消收藏 / 我的收藏列表。收藏不产生通知，仅作为个人书签。
 */
@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final UserRepository userRepository;
    private final BehaviorLogger behaviorLogger;

    public FavoriteService(FavoriteRepository favoriteRepository,
                           PostRepository postRepository,
                           PostService postService,
                           UserRepository userRepository,
                           BehaviorLogger behaviorLogger) {
        this.favoriteRepository = favoriteRepository;
        this.postRepository = postRepository;
        this.postService = postService;
        this.userRepository = userRepository;
        this.behaviorLogger = behaviorLogger;
    }

    /** 收藏帖子（同一用户对同一帖子只能收藏一次，草稿不可收藏） */
    public void favorite(Long postId, String username) {
        Post post = getPostOrThrow(postId);
        if (Post.STATUS_DRAFT.equals(post.getStatus())) {
            throw new BusinessException("草稿不能收藏");
        }
        if (favoriteRepository.findByPostIdAndUsername(postId, username).isPresent()) {
            throw new BusinessException("你已经收藏过这篇帖子了");
        }
        Favorite favorite = new Favorite();
        favorite.setPostId(postId);
        favorite.setUsername(username);
        favorite.setCreatedAt(LocalDateTime.now());
        favoriteRepository.save(favorite);
        userRepository.findByUsername(username)
                .ifPresent(u -> behaviorLogger.log(u.getId(), postId, BehaviorType.FAVORITE, "POST", null));
    }

    /** 取消收藏 */
    public void unfavorite(Long postId, String username) {
        Favorite favorite = favoriteRepository.findByPostIdAndUsername(postId, username)
                .orElseThrow(() -> new BusinessException("你还没有收藏过这篇帖子"));
        favoriteRepository.delete(favorite);
    }

    /** 当前用户是否已收藏该帖子 */
    public boolean isFavorite(Long postId, String username) {
        return username != null && favoriteRepository.findByPostIdAndUsername(postId, username).isPresent();
    }

    /** 我的收藏列表（分页，最新收藏在前，不含草稿与已删除帖子） */
    public PageResult<PostVO> getMyFavorites(String username, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> posts = favoriteRepository.findPostIdsByUsername(username).stream()
                .map(postRepository::findById)
                .filter(opt -> opt.isPresent() && Post.STATUS_PUBLISHED.equals(opt.get().getStatus()))
                .map(opt -> opt.get())
                .collect(Collectors.toList());
        long total = posts.size();
        int totalPages = safeSize > 0 ? (int) Math.ceil((double) total / safeSize) : 0;
        int fromIndex = Math.min((safePage - 1) * safeSize, (int) total);
        int toIndex = Math.min(fromIndex + safeSize, (int) total);
        List<PostVO> records = posts.isEmpty() ? new ArrayList<>()
                : posts.subList(fromIndex, toIndex).stream()
                        .map(p -> postService.toVO(p, username))
                        .collect(Collectors.toList());
        return new PageResult<>(records, total, safePage, safeSize);
    }

    /** 删除某帖子下的全部收藏（帖子被彻底删除时由 PostService 级联调用） */
    public void deleteByPostId(Long postId) {
        favoriteRepository.deleteByPostId(postId);
    }

    private Post getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在：id=" + postId));
    }
}
