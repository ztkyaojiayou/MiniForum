package com.tkzou.miniforum.dto;

/**
 * 推荐关注用户（社交卡）
 * <p>
 * "你关注的人关注了 X"：二度关注中按共同好友数排序推荐的用户，带推荐理由。
 */
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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public int getCommonFollowCount() {
        return commonFollowCount;
    }

    public void setCommonFollowCount(int commonFollowCount) {
        this.commonFollowCount = commonFollowCount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public boolean isFollowed() {
        return followed;
    }

    public void setFollowed(boolean followed) {
        this.followed = followed;
    }
}
