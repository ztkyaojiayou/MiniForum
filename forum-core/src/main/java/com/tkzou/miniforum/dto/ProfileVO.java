package com.tkzou.miniforum.dto;

import com.tkzou.miniforum.entity.User;

/**
 * 个人主页视图对象
 * <p>
 * 聚合用户基本信息、粉丝数、关注数与发帖数。
 */
public class ProfileVO {

    private Long id;

    private String username;

    private String email;

    private Integer age;

    /** 昵称（显示名） */
    private String nickname;

    /** 个人简介 */
    private String bio;

    /** 头像 */
    private String avatar;

    /** 粉丝数 */
    private long followerCount;

    /** 关注数 */
    private long followingCount;

    /** 已发布帖子数 */
    private long postCount;

    public ProfileVO() {
    }

    public ProfileVO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.age = user.getAge();
        this.nickname = user.getNickname();
        this.bio = user.getBio();
        this.avatar = user.getAvatar();
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public long getFollowerCount() {
        return followerCount;
    }

    public void setFollowerCount(long followerCount) {
        this.followerCount = followerCount;
    }

    public long getFollowingCount() {
        return followingCount;
    }

    public void setFollowingCount(long followingCount) {
        this.followingCount = followingCount;
    }

    public long getPostCount() {
        return postCount;
    }

    public void setPostCount(long postCount) {
        this.postCount = postCount;
    }
}
