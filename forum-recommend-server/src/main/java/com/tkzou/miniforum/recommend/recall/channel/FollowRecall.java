package com.tkzou.miniforum.recommend.recall.channel;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.graph.SocialGraphService;
import com.tkzou.miniforum.recommend.recall.RecallChannel;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关注召回（二度关系）：我关注的人发布的帖子，以及我关注的人转发过的帖子。
 * <p>数据流程：SocialGraphService.followingIds(uid) → 过滤"作者在我关注里 或 转发原作者在我关注里"
 * → 按时间倒序取 N → RecallHit(source="follow")。微博"半熟社交"的核心：关注关系是明确订阅，二度转发让大V替用户筛选内容。
 */
@Component
/**
 * 关注召回（source=follow）
 * <p>
 * 二度关系召回：取"我关注的人发的帖"或"我关注的人<b>转发过</b>的帖"（originalAuthorId 在我关注集合），
 * 按新鲜度降序。等于让关注的人替用户做了一轮内容筛选（社交信号），是社交推荐的核心路。
 */
public class FollowRecall implements RecallChannel {

    private final SocialGraphService socialGraphService;
    private final PostRepository postRepository;
    private final ItemFeatureService itemFeatureService;

    public FollowRecall(SocialGraphService socialGraphService,
                        PostRepository postRepository,
                        ItemFeatureService itemFeatureService) {
        this.socialGraphService = socialGraphService;
        this.postRepository = postRepository;
        this.itemFeatureService = itemFeatureService;
    }

    @Override
    public String name() {
        return "follow";
    }

    @Override
    public List<RecallHit> recall(RecommendContext ctx, int size) {
        Set<Long> followingIds = socialGraphService.followingIds(ctx.getUserId());
        if (followingIds.isEmpty()) {
            return List.of();
        }

        return postRepository.findAll().stream()
                .filter(this::isVisible)
                .filter(p -> (p.getAuthorId() != null && followingIds.contains(p.getAuthorId()))
                        || (p.getOriginalAuthorId() != null && followingIds.contains(p.getOriginalAuthorId())))
                .sorted(Comparator.comparing(Post::getCreatedAt).reversed())
                .limit(size)
                .map(p -> new RecallHit(p.getId(), itemFeatureService.itemFeature(p.getId()).getFreshness(), name()))
                .collect(Collectors.toList());
    }

    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }
}
