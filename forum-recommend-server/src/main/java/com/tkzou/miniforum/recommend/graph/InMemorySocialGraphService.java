package com.tkzou.miniforum.recommend.graph;

import com.tkzou.miniforum.entity.Follow;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.repository.FollowRepository;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.util.TtlCache;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 社交图谱服务默认实现（无 profile 组件，演示/生产通吃）
 * <p>
 * <b>数据流程</b>：{@link #followingIds}/{@link #isFollowing}/{@link #followedRepostedIds} 实时委托
 * core 的 {@code FollowRepository}/{@code PostRepository}（与现状逐请求计算一致，不加缓存避免改变计数语义）；
 * {@link #authorFollowers} 走短 TTL 缓存（作者粉丝数读多写少，缓存后排序/热门路径免重复 count）。
 */
@Component
public class InMemorySocialGraphService implements SocialGraphService {

    /** 作者粉丝数缓存 TTL 打散幅度（ms） */
    private static final long AUTHOR_FOLLOWERS_JITTER_MS = 500;

    private final FollowRepository followRepository;
    private final PostRepository postRepository;

    /** 作者粉丝数缓存：authorId → log1p(countByFolloweeId)。构造 ttl=0（禁用），由 setter 注入启用 */
    private final TtlCache<Long, Double> authorFollowersCache = new TtlCache<>(0, AUTHOR_FOLLOWERS_JITTER_MS);

    public InMemorySocialGraphService(FollowRepository followRepository,
                                      PostRepository postRepository) {
        this.followRepository = followRepository;
        this.postRepository = postRepository;
    }

    /**
     * 作者粉丝数缓存 TTL（ms），Spring 注入。>0 启用（作者粉丝数读多写少，避免排序逐候选 count）；
     * ≤0 禁用（测试/调试可关）。
     */
    @Value("${app.rec.graph-author-followers-cache-ttl-ms:5000}")
    public void setAuthorFollowersCacheTtlMs(long ttl) {
        authorFollowersCache.setTtlMillis(ttl);
    }

    @Override
    public Set<Long> followingIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        return followRepository.findByFollowerId(userId).stream()
                .map(Follow::getFolloweeId)
                .collect(Collectors.toSet());
    }

    @Override
    public boolean isFollowing(Long userId, Long authorId) {
        return userId != null && authorId != null && followRepository.exists(userId, authorId);
    }

    @Override
    public Set<Long> followedRepostedIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<Long> following = followingIds(userId);
        if (following.isEmpty()) {
            return Set.of();
        }
        return postRepository.findAll().stream()
                .filter(p -> p.getOriginalPostId() != null && p.getOriginalAuthorId() != null)
                .filter(p -> following.contains(p.getOriginalAuthorId()))
                .map(Post::getOriginalPostId)
                .collect(Collectors.toSet());
    }

    @Override
    public double authorFollowers(Long authorId) {
        if (authorId == null) {
            return 0;
        }
        return authorFollowersCache.get(authorId,
                () -> Math.log1p(followRepository.countByFolloweeId(authorId)));
    }
}
