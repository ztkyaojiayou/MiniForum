package com.tkzou.miniforum.dto;

import javax.validation.constraints.Email;
import javax.validation.constraints.Size;

/**
 * 修改个人资料请求 DTO
 * <p>
 * 昵称、简介、头像、邮箱、年龄均为可选字段（传 null 表示不修改）。
 * 用户名不允许修改。
 */
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

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
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
}
