package com.tkzou.miniforum.recommend.recall.impl;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import com.tkzou.miniforum.recommend.domain.RecommendContext;
import com.tkzou.miniforum.recommend.profile.UserProfileService;
import com.tkzou.miniforum.recommend.profile.UserProfile;
import com.tkzou.miniforum.recommend.recall.RecallChannel;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 话题召回：用户画像中兴趣权重最高的话题（微博兴趣载体）→ 命中这些话题的帖子。
 * <p>数据流程：UserProfileService.userProfile(uid).topTopics(2) → 遍历可见帖，命中话题则按画像话题权重累加得分
 * → 降序取 N → RecallHit(source="topic")。用户无画像时返回空（由热门等路兜底）。
 */
@Component
/**
 * 话题召回（source=topic）
 * <p>
 * 用画像兴趣话题权重（UserProfile.topicWeight）匹配帖子话题，取"兴趣话题重叠"的可见帖，
 * 按兴趣权重降序。核心是"兴趣 → 内容"的话题级匹配，与类目召回互补。
 */
public class TopicRecall implements RecallChannel {

    private final UserProfileService userProfileService;
    private final PostRepository postRepository;

    public TopicRecall(UserProfileService userProfileService, PostRepository postRepository) {
        this.userProfileService = userProfileService;
        this.postRepository = postRepository;
    }

    @Override
    public String name() {
        return "topic";
    }

    @Override
    public List<RecallHit> recall(RecommendContext ctx, int size) {
        UserProfile profile = userProfileService.userProfile(ctx.getUserId());
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
