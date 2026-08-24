package com.tkzou.miniforum.recommend.recall.channel;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.recall.RecallChannel;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 新内容召回：冷启内容池（新发布或互动过少）按新鲜度取 TopN，保证新帖有曝光机会。
 * 与冷启动池配合（Thompson 探索分在冷启动阶段注入）。
 */
@Component
public class NewItemRecall implements RecallChannel {

    private final PostRepository postRepository;
    private final FeatureService featureService;

    public NewItemRecall(PostRepository postRepository, FeatureService featureService) {
        this.postRepository = postRepository;
        this.featureService = featureService;
    }

    @Override
    public String name() {
        return "newitem";
    }

    @Override
    public List<RecallHit> recall(RecommendContext ctx, int size) {
        return postRepository.findAll().stream()
                .filter(this::isVisible)
                .map(p -> featureService.itemFeature(p.getId()))
                .filter(ItemFeature::isInNewPool)
                .sorted(Comparator.comparingDouble(ItemFeature::getFreshness).reversed())
                .limit(size)
                .map(f -> new RecallHit(f.getPostId(), f.getFreshness(), name()))
                .collect(Collectors.toList());
    }

    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }
}
