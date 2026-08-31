package com.tkzou.miniforum.recommend.feature;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品（帖子）特征
 * <p>
 * 排序层输入的内容侧特征：互动热度（微博式权重）、时效新鲜度、作者权重、是否冷启内容。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class ItemFeature {

    private Long postId;

    /** 话题列表（微博兴趣载体） */
    private List<String> topics = new ArrayList<>();

    /** 类目 */
    private String category;

    private int repostCount;
    private int commentCount;
    private int likeCount;
    private int favoriteCount;
    private int viewCount;

    /** 阅读停留总时长（秒，DWELL 求和；仿抖音"观看时长"） */
    private double readTimeSec;

    /** 互动热度分 = 3·转发 + 2·评论 + 1·点赞 + 1.5·收藏 + 0.02·浏览 + 0.05·阅读时长（微博信号权重） */
    private double hotScore;

    /** 发布时间距今小时数 */
    private double ageHours;

    /** 时效新鲜度 = exp(-ln2·ageHours/halfLife) */
    private double freshness;

    /** 是否冷启内容（新发布或互动过少） */
    private boolean inNewPool;

    public ItemFeature() {
    }

}
