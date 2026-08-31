package com.tkzou.miniforum.dto;
import lombok.Getter;
import lombok.Setter;

/**
 * 推荐关注用户（社交卡）
 * <p>
 * "你关注的人关注了 X"：二度关注中按共同好友数排序推荐的用户，带推荐理由。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class RecommendUserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    /** 有多少个我关注的人关注了 TA（共同好友数） */
    private int commonFollowCount;

    /** 推荐理由（"N 位你关注的人关注了 TA"） */
    private String reason;

    /** 当前用户是否已关注（本场景恒 false，字段保留避免前端 N+1 查状态） */
    private boolean followed;

    public RecommendUserVO() {
    }

    public RecommendUserVO(Long id, String username, String nickname, String avatar,
                           int commonFollowCount, String reason, boolean followed) {
        this.id = id;
        this.username = username;
        this.nickname = nickname;
        this.avatar = avatar;
        this.commonFollowCount = commonFollowCount;
        this.reason = reason;
        this.followed = followed;
    }

}
