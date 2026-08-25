package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.dto.UserBriefVO;
import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.feed.FollowFeedStore;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.repository.FollowRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注 / 粉丝服务
 * <p>
 * 负责关注、取关、关注/粉丝列表与「关注流」（只看关注的人发布的帖子）。
 */
@Service
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FavoriteRepository favoriteRepository;
    private final NotificationService notificationService;
    private final BehaviorLogger behaviorLogger;
    /** 关注流推模式 inbox（内存演示 @Profile("!prod") / Redis 生产 @Profile("prod")） */
    private final FollowFeedStore followFeedStore;
    /** 关注流 inbox 封顶条数（读取回源量上限） */
    private final int feedCap;

    public FollowService(FollowRepository followRepository,
                         UserRepository userRepository,
                         PostRepository postRepository,
                         LikeRepository likeRepository,
                         CommentRepository commentRepository,
                         FavoriteRepository favoriteRepository,
                         NotificationService notificationService,
                         BehaviorLogger behaviorLogger,
                         FollowFeedStore followFeedStore,
                         @Value("${app.rec.feed.cap:500}") int feedCap) {
        this.followRepository = followRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
        this.notificationService = notificationService;
        this.behaviorLogger = behaviorLogger;
        this.followFeedStore = followFeedStore;
        this.feedCap = feedCap;
    }

    /** 关注（不能关注自己，不能重复关注；关注后通知被关注者） */
    public void follow(Long followerId, Long followeeId, String followerUsername) {
        ensureUserExists(followerId);
        User followee = userRepository.findById(followeeId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在：id=" + followeeId));
        if (followerId.equals(followeeId)) {
            throw new BusinessException("不能关注自己");
        }
        if (followRepository.exists(followerId, followeeId)) {
            throw new BusinessException("你已经关注过该用户了");
        }
        Follow follow = new Follow();
        follow.setFollowerId(followerId);
        follow.setFolloweeId(followeeId);
        follow.setCreatedAt(LocalDateTime.now());
        followRepository.save(follow);
        behaviorLogger.log(followerId, null, BehaviorType.FOLLOW, "POST", null);
        // 关注回填：inbox 已建立时，把新关注作者的近期帖子补进我的关注流；
        // inbox 未建立时跳过——首次读取会用当前完整关注集合回填建流，避免建成只有单作者的半成品流
        if (followFeedStore.isBuilt(followerId)) {
            followFeedStore.onFollow(followerId, recentPostIdsOf(followeeId));
        }
        // 通知被关注者
        notificationService.notify(followeeId, followerId, followerUsername,
                Notification.TYPE_FOLLOW, null, "关注了你");
    }

    /** 取关 */
    public void unfollow(Long followerId, Long followeeId) {
        Follow follow = followRepository.findByFollowerAndFollowee(followerId, followeeId)
                .orElseThrow(() -> new BusinessException("你还没有关注该用户"));
        followRepository.delete(follow);
    }

    /** 是否已关注 */
    public boolean isFollowing(Long followerId, Long followeeId) {
        return followRepository.exists(followerId, followeeId);
    }

    /** 我关注的人数 */
    public long countFollowing(Long userId) {
        return followRepository.countByFollowerId(userId);
    }

    /** 我的粉丝数 */
    public long countFollowers(Long userId) {
        return followRepository.countByFolloweeId(userId);
    }

    /** 我关注的人列表（最新关注在前） */
    public List<UserBriefVO> getFollowing(Long userId) {
        return followRepository.findByFollowerId(userId).stream()
                .map(f -> userRepository.findById(f.getFolloweeId()))
                .filter(opt -> opt.isPresent())
                .map(opt -> new UserBriefVO(opt.get()))
                .collect(Collectors.toList());
    }

    /** 我的粉丝列表（最新关注在前） */
    public List<UserBriefVO> getFollowers(Long userId) {
        return followRepository.findByFolloweeId(userId).stream()
                .map(f -> userRepository.findById(f.getFollowerId()))
                .filter(opt -> opt.isPresent())
                .map(opt -> new UserBriefVO(opt.get()))
                .collect(Collectors.toList());
    }

    /** 我关注的用户 ID 集合 */
    public Set<Long> getFollowingIds(Long userId) {
        return followRepository.findByFollowerId(userId).stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toSet());
    }

    /**
     * 关注流：我关注的人发布的帖子（不含自己，分页，最新在前）
     * <p>
     * 推模式 inbox：已建流用户读自己的 inbox（O(1)）；未建流用户首次读取回退旧的全表查询
     * 并触发回填建流（用当前完整关注集合的近期帖子）。
     */
    public PageResult<PostVO> getFollowFeed(Long userId, int page, int size, String username) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Set<Long> followingIds = getFollowingIds(userId);
        List<Post> all;
        if (followingIds.isEmpty()) {
            all = new ArrayList<>();
        } else if (!followFeedStore.isBuilt(userId)) {
            // 首次读取：回退旧查询（结果完整），并触发回填建流
            all = fullScanFollowFeed(followingIds);
            followFeedStore.onFollow(userId, collectRecentFromFollowees(followingIds));
        } else {
            // 推模式：读 inbox → 回源 → 过滤（可见 + 作者仍在关注中，取关/删帖读取兜底）
            List<Long> inboxIds = followFeedStore.getInbox(userId, feedCap);
            all = resolveFromInbox(inboxIds, followingIds);
        }

        long total = all.size();
        int fromIndex = Math.min((safePage - 1) * safeSize, (int) total);
        int toIndex = Math.min(fromIndex + safeSize, (int) total);
        List<PostVO> records = all.isEmpty() ? new ArrayList<>()
                : all.subList(fromIndex, toIndex).stream()
                        .map(p -> toPostVO(p, username))
                        .collect(Collectors.toList());
        return new PageResult<>(records, total, safePage, safeSize);
    }

    /** 回退路径：全表扫描过滤关注作者的帖子（旧实现，仅在首次建流前兜底） */
    private List<Post> fullScanFollowFeed(Set<Long> followingIds) {
        return postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()))
                .filter(p -> !p.isDeleted())
                .filter(p -> p.getAuthorId() != null && followingIds.contains(p.getAuthorId()))
                .collect(Collectors.toList());
    }

    /** 从 inbox 的 postId 列表回源帖子并过滤：公开可见 + 作者仍在关注中（取关/删帖读取兜底），按发布时间倒序 */
    private List<Post> resolveFromInbox(List<Long> inboxIds, Set<Long> followingIds) {
        return inboxIds.stream()
                .map(postRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isVisiblePost)
                .filter(p -> p.getAuthorId() != null && followingIds.contains(p.getAuthorId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    /** 某作者近期可见帖的 ID（最新在前，最多 feedCap 条） */
    private List<Long> recentPostIdsOf(Long authorId) {
        return postRepository.findByAuthorId(authorId).stream()
                .filter(this::isVisiblePost)
                .map(Post::getId)
                .limit(feedCap)
                .collect(Collectors.toList());
    }

    /** 建流回填：汇总全部关注作者的近期帖子 ID，降序后按 inbox 封顶截断 */
    private List<Long> collectRecentFromFollowees(Set<Long> followingIds) {
        return followingIds.stream()
                .flatMap(id -> postRepository.findByAuthorId(id).stream()
                        .filter(this::isVisiblePost)
                        .map(Post::getId))
                .sorted(Comparator.reverseOrder())
                .limit(feedCap)
                .collect(Collectors.toList());
    }

    /** 公开可见：已发布且未删除 */
    private boolean isVisiblePost(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }

    /** 帖子转 VO（附带点赞数、当前用户点赞状态与评论数） */
    private PostVO toPostVO(Post post, String username) {
        PostVO vo = new PostVO(post);
        vo.setLikeCount(post.getLikeCount());
        vo.setViewCount(post.getViewCount());
        vo.setLikedByMe(username != null
                && likeRepository.findByPostIdAndUsername(post.getId(), username).isPresent());
        vo.setCommentCount(commentRepository.countByPostId(post.getId()));
        return vo;
    }

    private void ensureUserExists(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在：id=" + userId));
    }
}
