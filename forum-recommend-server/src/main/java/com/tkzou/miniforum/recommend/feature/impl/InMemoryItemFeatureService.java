package com.tkzou.miniforum.recommend.feature.impl;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.ItemFeatureFormula;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.feature.RealtimeFeature;
import com.tkzou.miniforum.recommend.feature.RealtimeFeatureStore;

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

    /** 特征公式（纯算法、无依赖 → 内联，不参与 DI；权重见 {@link ItemFeatureFormula}） */
    private final ItemFeatureFormula formula = new ItemFeatureFormula();

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

    /**
     * 物品特征（内容侧"这帖子怎么样"）——注意这是<b>读时现算 + 5s 短 TTL 缓存</b>，<b>不是主动更新的实时流</b>：
     * 点赞/评论改变计数后，特征最长滞后 {@code app.rec.item-feature-cache-ttl-ms}（默认 5000ms）才重算一次
     * （缓存过期即重算，见 {@link #computeItemFeature}）。与 {@link #realtimeMatch} 消费的
     * RealtimeFeature（近线窗口滚动聚合、每 5s 推进）是两套互补特征，别混为一谈。
     */
    @Override
    public ItemFeature itemFeature(Long postId) {
        // 短 TTL 缓存：热度/计数特征"读多写少"，命中免去多次 count 聚合；ttl<=0 自动现算
        return itemFeatureCache.get(postId, () -> computeItemFeature(postId));
    }

    /** 现算物品特征（缓存 miss / 过期时执行）：聚合计数 + 时效新鲜度 + 冷启标记。非主动更新——数据源实时变，特征最长滞后一个 TTL */
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
        // 公式统一委托 ItemFeatureFormula（纯算法、可独立单测；权重为命名常量，将来可下沉 RecConfig 调参）
        RecConfig cfg = configService.current();
        f.setHotScore(formula.hotScore(repost, comment, like, favorite, view, readTimeSec));
        double ageHours = formula.ageHours(post.getCreatedAt(), LocalDateTime.now());
        f.setAgeHours(ageHours);
        f.setFreshness(formula.freshness(ageHours, cfg.getHalfLifeHours()));
        f.setInNewPool(formula.inNewPool(ageHours, (long) like + comment + favorite + repost,
                cfg.getNewItemAgeHours(), cfg.getNewItemMinInteractions()));
        return f;
    }

    /**
     * 实时兴趣/热度加成（消费 {@link RealtimeFeatureStore} 的近线窗口特征，与 {@link #itemFeature} 互补）：
     * ① 用户近 N 分钟点击过的话题 × 帖子话题重叠（实时兴趣投影）；② 帖子近 N 分钟互动爆发（log1p 平滑）。
     * RealtimeFeature 才是"每 5s 滚动更新"的实时流（RealtimeFeatureWindow / Flink 窗口，第 3 章）；本方法只做读合并。
     */
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
