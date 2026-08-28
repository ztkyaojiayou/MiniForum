package com.tkzou.miniforum.recommend.coldstart;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.feature.ItemFeature;
import com.tkzou.miniforum.recommend.feature.ItemFeatureService;
import com.tkzou.miniforum.recommend.profile.UserProfile;
import com.tkzou.miniforum.recommend.profile.UserProfileService;
import com.tkzou.miniforum.recommend.rank.ExploreProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 冷启动服务：实现 ExploreProvider，为新内容提供 Thompson 探索加分。
 * <p>
 * <b>数据流程</b>：排序层为每个候选调用 {@link #exploreBonus(userId, postId)} →
 * 若物品在冷启池（{@code NewItemPool}，新发布或低互动）→ 按用户冷热取探索权重 λ
 * （冷用户 0.7 / 老用户 0.1）→ λ × {@code NewItemPool.sampleScore}（Thompson Beta 采样）→ 返回加分，
 * 叠加进排序加权分。池内后验由 {@code ColdStartFeedbackListener} 用行为事件（深度互动/曝光）回灌。
 * 新用户的热门/热搜兜底由排序特征（interact/hot 权重）天然保证，并在 RecommendService 中补充。
 */
@Component
@Primary
public class ColdStartService implements ExploreProvider {

    private final NewItemPool newItemPool;
    private final ItemFeatureService itemFeatureService;
    private final UserProfileService userProfileService;
    private final ConfigService configService;

    public ColdStartService(NewItemPool newItemPool,
                            ItemFeatureService itemFeatureService,
                            UserProfileService userProfileService,
                            ConfigService configService) {
        this.newItemPool = newItemPool;
        this.itemFeatureService = itemFeatureService;
        this.userProfileService = userProfileService;
        this.configService = configService;
    }

    @Override
    public double exploreBonus(Long userId, Long postId) {
        ItemFeature f = itemFeatureService.itemFeature(postId);
        if (!f.isInNewPool()) {
            return 0;
        }
        RecConfig cfg = configService.current();
        UserProfile profile = userProfileService.userProfile(userId);
        double lambda = profile.isCold(cfg.getMinBehaviorForWarm())
                ? cfg.getExploreLambdaNewUser()
                : cfg.getExploreLambdaWarmUser();
        return lambda * newItemPool.sampleScore(postId);
    }

    /** 是否冷用户（行为过少） */
    public boolean isColdUser(Long userId) {
        RecConfig cfg = configService.current();
        return userProfileService.userProfile(userId).isCold(cfg.getMinBehaviorForWarm());
    }
}
