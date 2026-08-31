package com.tkzou.miniforum.dto;
import lombok.Getter;
import lombok.Setter;

import com.tkzou.miniforum.entity.User;

/**
 * 个人主页视图对象
 * <p>
 * 聚合用户基本信息、粉丝数、关注数与发帖数。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
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

}
