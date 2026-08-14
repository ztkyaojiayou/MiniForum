package com.tkzou.miniforum.dto;

import com.tkzou.miniforum.entity.User;

/**
 * 用户简要信息视图（用于关注/粉丝列表展示）
 */
public class UserBriefVO {

    private Long id;

    private String username;

    public UserBriefVO() {
    }

    public UserBriefVO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
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
}
