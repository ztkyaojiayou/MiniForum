package com.tkzou.miniforum.recommend.recall.channel;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.recall.RecallChannel;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 热门召回：按微博式互动热度分取 TopN，是所有多路召回的"保底路"。
 * <p>数据流程：PostRepository 可见帖 → FeatureService.itemFeature().hotScore（3·转发+2·评论+1·赞+1.5·收藏+0.02·浏览）
 * → 降序取 N → RecallHit(source="hot")，交 MergeRecallService 融合。
 */
@Component
public class HotRecall implements RecallChannel {

    private final PostRepository postRepository;
    private final FeatureService featureService;

    public HotRecall(PostRepository postRepository, FeatureService featureService) {
        this.postRepository = postRepository;
        this.featureService = featureService;
    }

    @Override
    public String name() {
        return "hot";
    }

    @Override
    public List<RecallHit> recall(RecommendContext ctx, int size) {
        return postRepository.findAll().stream()
                .filter(this::isVisible)
                .map(p -> new RecallHit(p.getId(), featureService.itemFeature(p.getId()).getHotScore(), name()))
                .sorted(Comparator.comparingDouble(RecallHit::getScore).reversed())
                .limit(size)
                .collect(Collectors.toList());
    }

    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }
}
