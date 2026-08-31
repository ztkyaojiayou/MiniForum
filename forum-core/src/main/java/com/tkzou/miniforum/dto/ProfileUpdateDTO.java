package com.tkzou.miniforum.dto;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

/**
 * 修改个人资料请求 DTO
 * <p>
 * 昵称、简介、头像、邮箱、年龄均为可选字段（传 null 表示不修改）。
 * 用户名不允许修改。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class ProfileUpdateDTO {

    @Size(max = 30, message = "昵称长度不能超过 30")
    private String nickname;

    @Size(max = 200, message = "简介长度不能超过 200")
    private String bio;

    @Size(max = 20, message = "头像长度不能超过 20")
    private String avatar;

    @Email(message = "邮箱格式不正确")
    @Size(max = 50, message = "邮箱长度不能超过 50")
    private String email;

    private Integer age;

}
