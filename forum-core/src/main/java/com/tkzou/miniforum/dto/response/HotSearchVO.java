package com.tkzou.miniforum.dto.response;
import lombok.Getter;
import lombok.Setter;

/**
 * 热搜词视图对象
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class HotSearchVO {

    /** 热搜关键词（帖子标签） */
    private String keyword;

    /** 热度值（阅读量×1 + 点赞×2 + 评论×3，近 30 天时间衰减加权） */
    private long heat;

    /** 关联帖子数 */
    private long postCount;

    /** 排名（从 1 开始） */
    private int rank;

    /** 趋势：1=上升，0=持平，-1=下降，2=新上榜 */
    private int trend;

    /** 热度等级：爆 / 沸 / 热 / 新（仿微博热搜标签） */
    private String level = "热";

    public HotSearchVO() {
    }

    public HotSearchVO(String keyword, long heat, long postCount, int rank) {
        this.keyword = keyword;
        this.heat = heat;
        this.postCount = postCount;
        this.rank = rank;
        this.trend = 0;
    }

}
