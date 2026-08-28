package com.tkzou.miniforum.recommend.recall.channel;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.recall.RecallChannel;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 热门召回：按微博式互动热度分取 TopN，是所有多路召回的"保底路"。
 * <p>数据流程：PostRepository 可见帖 → ItemFeatureService.itemFeature().hotScore（3·转发+2·评论+1·赞+1.5·收藏+0.02·浏览）
 * → 降序取 N → RecallHit(source="hot")，交 MergeRecallService 融合。
 */
@Component
/**
 * 热门召回（source=hot）
 * <p>
 * 全站可见帖按微博式互动热度分（3·转发 + 2·评论 + 1·赞 + 1.5·收藏 + 0.02·浏览）降序取 TopK，
 * 热度取自 {@code ItemFeatureService.itemFeature(postId).getHotScore()}。
 * 冷用户热门兜底（ColdStartService）走同一热度口径。
 */
public class HotRecall implements RecallChannel {

    private final PostRepository postRepository;
    private final ItemFeatureService itemFeatureService;

    public HotRecall(PostRepository postRepository, ItemFeatureService itemFeatureService) {
        this.postRepository = postRepository;
        this.itemFeatureService = itemFeatureService;
    }

    @Override
    public String name() {
        return "hot";
    }

    @Override
    public List<RecallHit> recall(RecommendContext ctx, int size) {
        return postRepository.findAll().stream()
                .filter(this::isVisible)
                .map(p -> new RecallHit(p.getId(), itemFeatureService.itemFeature(p.getId()).getHotScore(), name()))
                .sorted(Comparator.comparingDouble(RecallHit::getScore).reversed())
                .limit(size)
                .collect(Collectors.toList());
    }

    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }
}
