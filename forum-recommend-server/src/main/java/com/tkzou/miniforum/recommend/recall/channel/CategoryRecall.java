package com.tkzou.miniforum.recommend.recall.channel;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.feature.UserProfile;
import com.tkzou.miniforum.recommend.recall.RecallChannel;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 类目召回：用户画像中兴趣权重最高的类目 → 该类目的帖子（话题缺失时的兜底兴趣维度）。
 * <p>数据流程：FeatureService.userProfile(uid).topCategories(2) → 遍历可见帖按类目匹配取画像权重
 * → 降序取 N → RecallHit(source="category")。
 */
@Component
/**
 * 类目召回（source=category）
 * <p>
 * 用画像兴趣类目权重（UserProfile.categoryWeight）匹配帖子分类，取"兴趣类目"的可见帖，
 * 按兴趣权重降序。与话题召回同属"兴趣匹配"路，一个按类目一个按话题。
 */
public class CategoryRecall implements RecallChannel {

    private final FeatureService featureService;
    private final PostRepository postRepository;

    public CategoryRecall(FeatureService featureService, PostRepository postRepository) {
        this.featureService = featureService;
        this.postRepository = postRepository;
    }

    @Override
    public String name() {
        return "category";
    }

    @Override
    public List<RecallHit> recall(RecommendContext ctx, int size) {
        UserProfile profile = featureService.userProfile(ctx.getUserId());
        List<String> topCategories = profile.topCategories(2);
        Map<String, Double> categoryWeight = profile.getCategoryWeight();
        if (topCategories.isEmpty()) {
            return List.of();
        }

        List<RecallHit> hits = new ArrayList<>();
        for (Post p : postRepository.findAll()) {
            if (!isVisible(p)) {
                continue;
            }
            String cat = p.getCategory() == null || p.getCategory().isBlank() ? "其他" : p.getCategory();
            if (topCategories.contains(cat)) {
                hits.add(new RecallHit(p.getId(), categoryWeight.getOrDefault(cat, 0.0), name()));
            }
        }
        hits.sort(Comparator.comparingDouble(RecallHit::getScore).reversed());
        return hits.size() > size ? new ArrayList<>(hits.subList(0, size)) : hits;
    }

    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }
}
