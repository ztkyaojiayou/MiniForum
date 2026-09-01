package com.tkzou.miniforum.dto.response;
import lombok.Getter;
import lombok.Setter;

import com.tkzou.miniforum.entity.User;

/**
 * 用户简要信息视图（用于关注/粉丝列表展示）
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class UserBriefVO {

    private Long id;

    private String username;

    public UserBriefVO() {
    }

    public UserBriefVO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
    }

    public UserBriefVO(Long id, String username) {
        this.id = id;
        this.username = username;
    }

}
