package com.tkzou.miniforum.dto.response;

import lombok.Getter;
import lombok.Setter;

import com.tkzou.miniforum.entity.User;

/**
 * 用户视图对象（API 边界脱敏）
 * <p>
 * 供用户 CRUD / 登录等接口返回，不含 {@code password}（密码散列仅存在于实体与存储层），
 * 避免 {@link User} 实体直接序列化导致密码哈希泄漏给前端。
 * 邮箱字段与 {@link ProfileVO} 一致保留（个人资料页 / 管理端需要展示）。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class UserVO {

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

    public UserVO() {
    }

    public UserVO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.age = user.getAge();
        this.nickname = user.getNickname();
        this.bio = user.getBio();
        this.avatar = user.getAvatar();
    }
}
