package com.tkzou.miniforum.recommend.coldstart;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.feature.FeatureService;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.UserProfile;
import com.tkzou.miniforum.recommend.rank.ExploreProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 冷启动服务：实现 ExploreProvider，为新内容提供 Thompson 探索加分。
 * <p>
 * 仅对冷启内容（新发布/低互动）加分；λ 随用户行为量衰减（新用户多探索、老用户少探索）。
 * 新用户的热门/热搜兜底由排序特征（interact/hot 权重）天然保证，并在 RecommendService 中补充。
 */
@Component
@Primary
public class ColdStartService implements ExploreProvider {

    private final NewItemPool newItemPool;
    private final FeatureService featureService;
    private final ConfigService configService;

    public ColdStartService(NewItemPool newItemPool,
                            FeatureService featureService,
                            ConfigService configService) {
        this.newItemPool = newItemPool;
        this.featureService = featureService;
        this.configService = configService;
    }

    @Override
    public double exploreBonus(Long userId, Long postId) {
        ItemFeature f = featureService.itemFeature(postId);
        if (!f.isInNewPool()) {
            return 0;
        }
        RecConfig cfg = configService.current();
        UserProfile profile = featureService.userProfile(userId);
        double lambda = profile.isCold(cfg.getMinBehaviorForWarm())
                ? cfg.getExploreLambdaNewUser()
                : cfg.getExploreLambdaWarmUser();
        return lambda * newItemPool.sampleScore(postId);
    }

    /** 是否冷用户（行为过少） */
    public boolean isColdUser(Long userId) {
        RecConfig cfg = configService.current();
        return featureService.userProfile(userId).isCold(cfg.getMinBehaviorForWarm());
    }
}
