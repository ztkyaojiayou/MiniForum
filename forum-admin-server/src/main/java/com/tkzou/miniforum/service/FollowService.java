package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.common.CursorPage;
import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.dto.response.RecommendUserVO;
import com.tkzou.miniforum.dto.response.UserBriefVO;
import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.entity.Notification;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.feed.FollowFeedStore;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorScene;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        behaviorLogger.log(followerId, null, BehaviorType.FOLLOW, BehaviorScene.POST, null);
        // 大V集合事件驱动维护：关注让被关注者粉丝数 +1，可能跨过阈值
        followFeedStore.refreshBigV(followeeId);
        // 关注回填：inbox 已建立时，把新关注作者的近期帖子补进我的关注流；
        // 大V跳过回填（读时实时拉 outbox）；inbox 未建立时跳过——首次读取会用当前完整关注集合回填建流
        if (!followFeedStore.isBigV(followeeId) && followFeedStore.isBuilt(followerId)) {
            followFeedStore.onFollow(followerId, recentPostIdsOf(followeeId));
        }
        // 通知被关注者
        notificationService.notify(followeeId, followerId, followerUsername,
                Notification.TYPE_FOLLOW, null, "关注了你");
    }

    /** 取关（状态表删边；同时记 UNFOLLOW 行为事件，与关注成对） */
    public void unfollow(Long followerId, Long followeeId) {
        Follow follow = followRepository.findByFollowerAndFollowee(followerId, followeeId)
                .orElseThrow(() -> new BusinessException("你还没有关注该用户"));
        followRepository.delete(follow);                        // ① 状态表删边（Follow=当前关注关系，非历史）
        followFeedStore.refreshBigV(followeeId);                // ② 大V集合维护：粉丝数 -1，可能掉出阈值
        behaviorLogger.log(followerId, null, BehaviorType.UNFOLLOW, BehaviorScene.POST, null); // ③ 事件流（社交图负信号）
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

    /** 我关注的人列表（最新关注在前；P2-25 批量 IN 回源，替代逐条 findById） */
    public List<UserBriefVO> getFollowing(Long userId) {
        List<Follow> follows = followRepository.findByFollowerId(userId);
        Map<Long, User> users = userRepository.findByIds(
                        follows.stream().map(Follow::getFolloweeId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        return follows.stream()
                .map(f -> users.get(f.getFolloweeId()))
                .filter(u -> u != null)
                .map(UserBriefVO::new)
                .collect(Collectors.toList());
    }

    /** 我的粉丝列表（最新关注在前；P2-25 批量 IN 回源） */
    public List<UserBriefVO> getFollowers(Long userId) {
        List<Follow> follows = followRepository.findByFolloweeId(userId);
        Map<Long, User> users = userRepository.findByIds(
                        follows.stream().map(Follow::getFollowerId).collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        return follows.stream()
                .map(f -> users.get(f.getFollowerId()))
                .filter(u -> u != null)
                .map(UserBriefVO::new)
                .collect(Collectors.toList());
    }

    /** 我关注的用户 ID 集合 */
    public Set<Long> getFollowingIds(Long userId) {
        return followRepository.findByFollowerId(userId).stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toSet());
    }

    /** 拉组 = 全局大V集合 ∩ 我关注的（集合求交，不逐人 countByFolloweeId——见 docs/关注流拉推结合实施方案.md §6） */
    private Set<Long> bigVsOf(Set<Long> followingIds) {
        Set<Long> bigVs = new HashSet<>(followFeedStore.bigVIds());
        bigVs.retainAll(followingIds);
        return bigVs;
    }

    /** 用户删除后的级联清理：删关注关系 + 从大V集合移除（粉丝数归零）。供 UserService.deleteUser 调用。 */
    public void onUserDeleted(Long userId) {
        followRepository.deleteByUserId(userId);
        followFeedStore.refreshBigV(userId);
    }

    /** 二度遍历关注者的数量上限（防关注数大时 N+1 扫描失控） */
    private static final int MAX_SECOND_DEGREE_SCAN = 50;

    /**
     * 推荐关注（社交卡"你关注的人关注了 X"）：二度关注中按共同好友数排序推荐用户。
     * <p>
     * 复杂度 O(关注数 × 平均二度关注数)（N+1 查询），演示级可接受；二度遍历封顶 {@link #MAX_SECOND_DEGREE_SCAN}。
     */
    public List<RecommendUserVO> suggestFollows(Long userId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        Set<Long> followingIds = getFollowingIds(userId);
        if (followingIds.isEmpty()) {
            return new ArrayList<>();
        }
        // 二度遍历：最多取最近 MAX_SECOND_DEGREE_SCAN 个关注者，按共同好友数累计
        List<Long> scanPool = followRepository.findByFollowerId(userId).stream()
                .map(Follow::getFolloweeId)
                .limit(MAX_SECOND_DEGREE_SCAN)
                .collect(Collectors.toList());
        Map<Long, Integer> commonCount = new HashMap<>();
        for (Long followeeId : scanPool) {
            for (Follow f : followRepository.findByFollowerId(followeeId)) {
                Long candidateId = f.getFolloweeId();
                // 过滤：自己、已关注的人
                if (candidateId.equals(userId) || followingIds.contains(candidateId)) {
                    continue;
                }
                commonCount.merge(candidateId, 1, Integer::sum);
            }
        }
        // 过滤用户不存在 + 共同好友数降序（id 升序兜底，保证确定性）+ 截断（P2-25 批量 IN 回源）
        Map<Long, User> users = userRepository.findByIds(commonCount.keySet())
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        return commonCount.entrySet().stream()
                .filter(e -> users.containsKey(e.getKey()))
                .sorted(Comparator.<Map.Entry<Long, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Comparator.comparing(Map.Entry::getKey)))
                .limit(safeLimit)
                .map(e -> {
                    User u = users.get(e.getKey());
                    return new RecommendUserVO(u.getId(), u.getUsername(), u.getNickname(), u.getAvatar(),
                            e.getValue(), e.getValue() + " 位你关注的人关注了 TA", false);
                })
                .collect(Collectors.toList());
    }

    /**
     * 关注流（向下游标）：我关注的人发布的帖子，最新在前，返回一页 + 下一页游标。
     * <p>
     * 拉推结合：普通作者走推（inbox 已扇出），大V作者走拉（读时实时拉其 outbox）——
     * 两路都以全局 postId 游标 maxId 过滤后合并去重（postId 单调递增 = 天然时间序）。
     * 未建流用户首次读取先用当前完整关注集合的近期帖回填建流（只回填普通作者；大V帖子读时拉）。
     * 注意：inbox 按 feed.cap 封顶，首读即应用封顶（最多最近 N 条）。
     */
    public CursorPage<PostVO> getFollowFeed(Long userId, Long maxId, int size, String username) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Set<Long> followingIds = getFollowingIds(userId);
        if (followingIds.isEmpty()) {
            return new CursorPage<>(new ArrayList<>(), null, false);
        }
        // 拉推分组：大V走拉 / 普通作者走推
        Set<Long> bigVs = bigVsOf(followingIds);
        Set<Long> normalIds = new HashSet<>(followingIds);
        normalIds.removeAll(bigVs);
        // 首次读取：触发回填建流（只回填普通作者；大V帖子读时实时拉，不物理进 inbox）
        if (!followFeedStore.isBuilt(userId)) {
            followFeedStore.onFollow(userId, collectRecentFromFollowees(normalIds));
        }
        // 整窗取 inbox（≤ feedCap）+ 拉每个大V outbox，合并去重排序（全局 postId 游标对两路都成立）
        int take = safeSize + bigVs.size() + 1;
        List<Long> inboxIds = followFeedStore.getInbox(userId, maxId, feedCap);
        List<Long> pulled = new ArrayList<>();
        for (Long v : bigVs) {
            pulled.addAll(followFeedStore.getAuthorTimeline(v, maxId, take));
        }
        List<Long> mixed = Stream.concat(inboxIds.stream(), pulled.stream())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        List<Post> resolved = resolveFromInbox(mixed, followingIds);
        boolean hasMore = resolved.size() > safeSize;
        List<Post> pagePosts = resolved.size() > safeSize ? resolved.subList(0, safeSize) : resolved;
        Long nextMaxId = pagePosts.isEmpty() ? null : pagePosts.get(pagePosts.size() - 1).getId();
        List<PostVO> records = pagePosts.stream()
                .map(p -> toPostVO(p, username))
                .collect(Collectors.toList());
        return new CursorPage<>(records, nextMaxId, hasMore);
    }

    /**
     * 关注流增量刷新（since）：返回比 sinceId 更新的关注动态（最新在前）。
     * 未建流时同样先回填建流（前端登录后即开始轮询，用户可能从未打开关注 Tab）。
     * 大V作者的帖子同样走拉：读其 outbox 最新 take 条，过滤 postId &gt; sinceId 后合并。
     */
    public List<PostVO> getFollowFeedSince(Long userId, Long sinceId, int size, String username) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Set<Long> followingIds = getFollowingIds(userId);
        if (followingIds.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> bigVs = bigVsOf(followingIds);
        Set<Long> normalIds = new HashSet<>(followingIds);
        normalIds.removeAll(bigVs);
        if (!followFeedStore.isBuilt(userId)) {
            followFeedStore.onFollow(userId, collectRecentFromFollowees(normalIds));
        }
        // 推流增量（inbox after sinceId）+ 拉流（每个大V最新 safeSize 条，过滤 > sinceId）
        List<Long> inboxIds = followFeedStore.getInboxAfter(userId, sinceId, safeSize);
        List<Long> pulled = new ArrayList<>();
        for (Long v : bigVs) {
            followFeedStore.getAuthorTimeline(v, null, safeSize).stream()
                    .filter(id -> sinceId == null || id > sinceId)
                    .forEach(pulled::add);
        }
        List<Long> mixed = Stream.concat(inboxIds.stream(), pulled.stream())
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());
        return resolveFromInbox(mixed, followingIds).stream()
                .map(p -> toPostVO(p, username))
                .collect(Collectors.toList());
    }

    /** 从 inbox 的 postId 列表回源帖子并过滤：公开可见 + 作者仍在关注中（取关/删帖读取兜底）。保持 inbox 的 postId 降序 */
    private List<Post> resolveFromInbox(List<Long> inboxIds, Set<Long> followingIds) {
        return inboxIds.stream()
                .map(postRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isVisiblePost)
                .filter(p -> p.getAuthorId() != null && followingIds.contains(p.getAuthorId()))
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
