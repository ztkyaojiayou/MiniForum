package com.tkzou.miniforum.recommend.graph;

import com.tkzou.miniforum.recommend.graph.impl.InMemorySocialGraphService;
import java.util.Set;

/**
 * 社交图谱服务（图关系域入口，标准推荐系统 ④图关系）
 * <p>
 * 把"关注/粉丝/二度关系"抽象为推荐可用的社交信号，供社交召回（FollowRecall）与排序特征（social/author）消费。
 * 只依赖 forum-core 的 {@code FollowRepository}/{@code PostRepository}（关注关系存储 + 帖子），
 * 与画像域（profile）、特征域（feature）互不依赖——由召回/排序等编排层按需注入。
 * <p>
 * 演示实现 {@link InMemorySocialGraphService}；生产下其底层存储自动为 {@code MySqlFollowRepository}
 * （demo-runner src/prod，MySQL 事实 + Redis ZSET 缓存），本接口无需区分实现。
 */
public interface SocialGraphService {

    /** 我关注的人（followeeId 集合）；userId 为 null 返回空集 */
    Set<Long> followingIds(Long userId);

    /** 我是否关注了该作者 */
    boolean isFollowing(Long userId, Long authorId);

    /** 我关注的人转发过的<b>原帖</b> ID 集合（二度关系信号，请求内预计算一次）；userId 为 null 返回空集 */
    Set<Long> followedRepostedIds(Long userId);

    /** 作者粉丝数（log1p），内部短 TTL 缓存；authorId 为 null 返回 0 */
    double authorFollowers(Long authorId);
}
