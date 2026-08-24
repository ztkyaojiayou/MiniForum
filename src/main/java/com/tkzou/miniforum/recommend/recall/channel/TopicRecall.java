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
 * 话题召回：用户画像中兴趣权重最高的话题（微博兴趣载体）→ 命中这些话题的帖子。
 * 用户无画像时返回空（由热门等路兜底）。
 */
@Component
public class TopicRecall implements RecallChannel {

    private final FeatureService featureService;
    private final PostRepository postRepository;

    public TopicRecall(FeatureService featureService, PostRepository postRepository) {
        this.featureService = featureService;
        this.postRepository = postRepository;
    }

    @Override
    public String name() {
        return "topic";
    }

    @Override
    public List<RecallHit> recall(RecommendContext ctx, int size) {
        UserProfile profile = featureService.userProfile(ctx.getUserId());
        List<String> topTopics = profile.topTopics(2);
        Map<String, Double> topicWeight = profile.getTopicWeight();
        if (topTopics.isEmpty()) {
            return List.of();
        }

        List<RecallHit> hits = new ArrayList<>();
        for (Post p : postRepository.findAll()) {
            if (!isVisible(p) || p.getTopics() == null) {
                continue;
            }
            double score = 0;
            for (String t : p.getTopics()) {
                if (topTopics.contains(t)) {
                    score += topicWeight.getOrDefault(t, 0.0);
                }
            }
            if (score > 0) {
                hits.add(new RecallHit(p.getId(), score, name()));
            }
        }
        hits.sort(Comparator.comparingDouble(RecallHit::getScore).reversed());
        return hits.size() > size ? new ArrayList<>(hits.subList(0, size)) : hits;
    }

    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }
}
