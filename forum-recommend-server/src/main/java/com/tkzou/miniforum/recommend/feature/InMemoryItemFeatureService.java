package com.tkzou.miniforum.recommend.feature;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.behavior.BehaviorLog;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.util.TtlCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 物品特征服务内存默认实现（无 profile 组件，演示/生产通吃）
 * <p>
 * <b>数据流程</b>：{@link #itemFeature} 聚合 PostRepository 计数 + ConfigService 时效（热/排序/重排特征），
 * 走短 TTL 缓存（5s，单飞防击穿 + TTL 打散）；{@link #realtimeMatch} 合并 {@link RealtimeFeatureStore} 的
 * 用户话题投影与物品热度爆发（实时特征）。
 * 生产形态：实时特征存 Redis（见 prod 适配）；本实现每次现算，数据量小时开销可忽略。
 * <p>
 * <b>域边界</b>：只做"内容侧特征"。作者粉丝数等社交特征不在此（由 {@code graph.SocialGraphService} 提供）。
 */
@Component
public class InMemoryItemFeatureService implements ItemFeatureService {

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FavoriteRepository favoriteRepository;
    private final RealtimeFeatureStore realtimeFeatureStore;
    private final ConfigService configService;
    private final BehaviorLogRepository behaviorLogRepository;

    /** 物品特征缓存 TTL 打散幅度（ms） */
    private static final long ITEM_JITTER_MS = 500;

    /** 物品特征缓存：postId → 特征。构造 ttl=0（禁用），由 setter 注入启用；单飞重建防击穿 */
    private final TtlCache<Long, ItemFeature> itemFeatureCache = new TtlCache<>(0, ITEM_JITTER_MS);

    /**
     * 物品特征缓存 TTL（ms），Spring 注入。>0 启用：热度/计数是"读多写少"，缓存后排序/热门路径
     * 从"N 次 count 聚合"降为"一次缓存读"；≤0 禁用。
     */
    @Value("${app.rec.item-feature-cache-ttl-ms:5000}")
    public void setItemFeatureCacheTtlMs(long ttl) {
        itemFeatureCache.setTtlMillis(ttl);
    }

    public InMemoryItemFeatureService(PostRepository postRepository,
                                      LikeRepository likeRepository,
                                      CommentRepository commentRepository,
                                      FavoriteRepository favoriteRepository,
                                      RealtimeFeatureStore realtimeFeatureStore,
                                      ConfigService configService,
                                      BehaviorLogRepository behaviorLogRepository) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
        this.realtimeFeatureStore = realtimeFeatureStore;
        this.configService = configService;
        this.behaviorLogRepository = behaviorLogRepository;
    }

    @Override
    public ItemFeature itemFeature(Long postId) {
        // 短 TTL 缓存：热度/计数特征"读多写少"，命中免去多次 count 聚合；ttl<=0 自动现算
        return itemFeatureCache.get(postId, () -> computeItemFeature(postId));
    }

    /** 现算物品特征（缓存 miss 时执行）：聚合计数 + 时效新鲜度 + 冷启标记 */
    private ItemFeature computeItemFeature(Long postId) {
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

    /**
     * 统计某帖被转发的次数（仅统计可见转发帖）。
     * 口径 = 【直接转发数】：只数 {@code originalPostId == 本帖} 的帖子（转发链 A←B←C 时，A 只算 B 直接转的一次）；
     * 不含"链式转发总量"（折叠到根的递归回溯，本项目不做）。
     */
    private int countReposts(Long postId) {
        return (int) postRepository.findAll().stream()
                .filter(p -> postId.equals(p.getOriginalPostId()))
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .count();
    }
}
