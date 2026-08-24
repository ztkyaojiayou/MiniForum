package com.tkzou.miniforum.recommend.recall.channel;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.feature.UserProfile;
import com.tkzou.miniforum.recommend.model.ItemCfModel;
import com.tkzou.miniforum.recommend.model.ItemCfModelStore;
import com.tkzou.miniforum.recommend.recall.RecallChannel;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * ItemCF 召回：用户历史交互过物品的相似物品（"看了 A 的人还看 B"）。
 * 使用深度互动构建的协同过滤模型，是弱训练侧核心个性化召回。
 */
@Component
public class ItemCfRecall implements RecallChannel {

    private final ItemCfModelStore itemCfModelStore;
    private final FeatureService featureService;
    private final PostRepository postRepository;

    public ItemCfRecall(ItemCfModelStore itemCfModelStore,
                        FeatureService featureService,
                        PostRepository postRepository) {
        this.itemCfModelStore = itemCfModelStore;
        this.featureService = featureService;
        this.postRepository = postRepository;
    }

    @Override
    public String name() {
        return "itemcf";
    }

    @Override
    public List<RecallHit> recall(RecommendContext ctx, int size) {
        UserProfile profile = featureService.userProfile(ctx.getUserId());
        List<Long> recentItems = profile.getRecentItemIds();
        if (recentItems.isEmpty()) {
            return List.of();
        }
        Set<Long> interacted = new HashSet<>(recentItems);
        ItemCfModel model = itemCfModelStore.get();
        if (model.size() == 0) {
            return List.of();
        }

        // 对用户历史物品的 Top 相似物品累加相似度
        Map<Long, Double> scores = new HashMap<>();
        for (Long history : recentItems) {
            for (ItemCfModel.SimilarItem s : model.topSimilar(history, 30)) {
                if (interacted.contains(s.itemId())) {
                    continue;
                }
                scores.merge(s.itemId(), s.similarity(), Double::sum);
            }
        }
        if (scores.isEmpty()) {
            return List.of();
        }

        Set<Long> visibleIds = postRepository.findAll().stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted())
                .map(Post::getId)
                .collect(Collectors.toSet());

        List<RecallHit> hits = scores.entrySet().stream()
                .filter(e -> visibleIds.contains(e.getKey()))
                .map(e -> new RecallHit(e.getKey(), e.getValue(), name()))
                .sorted(Comparator.comparingDouble(RecallHit::getScore).reversed())
                .limit(size)
                .collect(Collectors.toList());
        return new ArrayList<>(hits);
    }
}
