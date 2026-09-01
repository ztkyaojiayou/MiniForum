package com.tkzou.miniforum.dto.response;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 推荐流视图对象：帖子 + 推荐理由 + 来源 + 排序分（可解释推荐）
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class RecommendPostVO {

    private PostVO post;

    /** 排序分（微博式 rankScore） */
    private double score;

    /** 可读推荐理由：如 "因为你看过 #科技#" / "你关注的人发布了" / "大家都在看" */
    private String reason;

    /** 命中的召回路来源：hot/topic/itemcf/follow... */
    private List<String> sources = new ArrayList<>();

    public RecommendPostVO() {
    }

    public RecommendPostVO(PostVO post, double score, String reason, List<String> sources) {
        this.post = post;
        this.score = score;
        this.reason = reason;
        this.sources = new ArrayList<>(sources);
    }

}
