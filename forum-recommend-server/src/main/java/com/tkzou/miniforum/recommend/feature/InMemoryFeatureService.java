package com.tkzou.miniforum.recommend.feature;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.prod.redis.RedisUserProfileStore;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.FollowRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内存特征服务（默认实现）
 * <p>
 * <b>数据流程</b>：{@link #userProfile} 委托 {@link UserProfileAggregator}（行为日志→兴趣权重，供召回/排序）；
 * {@link #itemFeature} 聚合 PostRepository 计数 + FollowRepository 粉丝数 + ConfigService 时效（热/排序/重排特征）；
 * {@link #realtimeMatch} 合并 {@link RealtimeFeatureStore} 的用户话题投影与物品热度爆发（实时特征）。
 * 生产形态：画像/物品特征存 Redis，在线读取（见 prod 适配）；本实现每次现算，数据量小时开销可忽略。
 */
@Component
public class InMemoryFeatureService implements FeatureService {

    private final UserProfileAggregator aggregator;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FavoriteRepository favoriteRepository;
    private final RealtimeFeatureStore realtimeFeatureStore;
    private final ConfigService configService;
    private final BehaviorLogRepository behaviorLogRepository;
    /** 生产画像 Redis 存储（@Profile("prod") 才存在；演示为 null → 每次现算） */
    @Autowired(required = false)
    private RedisUserProfileStore redisUserProfileStore;

    public InMemoryFeatureService(UserProfileAggregator aggregator,
                                  PostRepository postRepository,
                                  FollowRepository followRepository,
                                  LikeRepository likeRepository,
                                  CommentRepository commentRepository,
                                  FavoriteRepository favoriteRepository,
                                  RealtimeFeatureStore realtimeFeatureStore,
                                  ConfigService configService,
                                  BehaviorLogRepository behaviorLogRepository) {
        this.aggregator = aggregator;
        this.postRepository = postRepository;
        this.followRepository = followRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
        this.realtimeFeatureStore = realtimeFeatureStore;
        this.configService = configService;
        this.behaviorLogRepository = behaviorLogRepository;
    }

    @Override
    public UserProfile userProfile(Long userId) {
        if (redisUserProfileStore != null) {
            // 生产：读 Redis 画像（跨实例共享），未命中现算并写回
            return redisUserProfileStore.get(userId).orElseGet(() -> {
                UserProfile p = aggregator.build(userId);
                redisUserProfileStore.put(userId, p);
                return p;
            });
        }
        return aggregator.build(userId);
    }

    @Override
    public ItemFeature itemFeature(Long postId) {
        ItemFeature f = new ItemFeature();
        Post post = postRepository.findById(postId).orElse(null);
        if (post == null) {
            return f;
        }
        f.setPostId(postId);
        f.setTopics(post.getTopics() == null ? List.of() : post.getTopics());
        f.setCategory(post.getCategory() == null || post.getCategory().isBlank() ? "其他" : post.getCategory());

        int like = (int) likeRepository.countByPostId(postId);
        int comment = (int) commentRepository.countByPostId(postId);
        int favorite = (int) favoriteRepository.countByPostId(postId);
        int repost = countReposts(postId);
        long view = post.getViewCount();
        f.setLikeCount(like);
        f.setCommentCount(comment);
        f.setFavoriteCount(favorite);
        f.setRepostCount(repost);
        f.setViewCount((int) view);
        // 阅读停留总时长（DWELL 求和，仿抖音"观看时长"）
        double readTimeSec = behaviorLogRepository.findByPostId(postId).stream()
                .filter(b -> b.getType() == BehaviorType.DWELL && b.getDurationSec() != null)
                .mapToDouble(BehaviorLog::getDurationSec)
                .sum();
        f.setReadTimeSec(readTimeSec);
        // 微博信号权重：转发>评论>点赞>收藏>浏览；阅读时长也计入热度
        f.setHotScore(3.0 * repost + 2.0 * comment + 1.0 * like + 1.5 * favorite + 0.02 * view + 0.05 * readTimeSec);

        LocalDateTime now = LocalDateTime.now();
        double ageHours = post.getCreatedAt() == null
                ? 0 : Math.max(0, Duration.between(post.getCreatedAt(), now).toMinutes() / 60.0);
        f.setAgeHours(ageHours);
        RecConfig cfg = configService.current();
        double halfLife = cfg.getHalfLifeHours();
        f.setFreshness(Math.exp(-Math.log(2) * ageHours / halfLife));

        if (post.getAuthorId() != null) {
            f.setAuthorFollowers(Math.log1p(followRepository.countByFolloweeId(post.getAuthorId())));
        }

        boolean inNewPool = ageHours < cfg.getNewItemAgeHours()
                || (like + comment + favorite + repost) < cfg.getNewItemMinInteractions();
        f.setInNewPool(inNewPool);
        return f;
    }

    @Override
    public double realtimeMatch(Long userId, Long postId) {
        double score = 0;
        // 用户近期兴趣话题 × 帖子话题重叠（实时兴趣投影）
        RealtimeFeature userFeature = realtimeFeatureStore.getForUser(userId).orElse(null);
        Post post = postRepository.findById(postId).orElse(null);
        if (userFeature != null && post != null && post.getTopics() != null) {
            for (String topic : post.getTopics()) {
                score += userFeature.getTopicClicks().getOrDefault(topic, 0) * 0.3;
            }
        }
        // 帖子近期互动爆发（实时热度）
        RealtimeFeature postFeature = realtimeFeatureStore.getForPost(postId).orElse(null);
        if (postFeature != null) {
            score += Math.log1p(postFeature.getClickCount()) * 0.3;
        }
        return score;
    }

    /** 统计某帖被转发的次数（仅统计可见转发帖） */
    private int countReposts(Long postId) {
        return (int) postRepository.findAll().stream()
                .filter(p -> postId.equals(p.getOriginalPostId()))
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .count();
    }
}
