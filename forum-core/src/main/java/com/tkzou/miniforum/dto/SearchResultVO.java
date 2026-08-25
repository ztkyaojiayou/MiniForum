package com.tkzou.miniforum.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 综合搜索结果视图（帖子 + 用户）
 */
public class SearchResultVO {

    /** 命中的帖子（标题/内容/标签/话题任一匹配，标题命中优先） */
    private List<PostVO> posts = new ArrayList<>();

    /** 命中的用户（用户名/昵称匹配） */
    private List<UserBriefVO> users = new ArrayList<>();

    public SearchResultVO() {
    }

    public SearchResultVO(List<PostVO> posts, List<UserBriefVO> users) {
        this.posts = posts == null ? new ArrayList<>() : posts;
        this.users = users == null ? new ArrayList<>() : users;
    }

    public List<PostVO> getPosts() {
        return posts;
    }

    public void setPosts(List<PostVO> posts) {
        this.posts = posts;
    }

    public List<UserBriefVO> getUsers() {
        return users;
    }

    public void setUsers(List<UserBriefVO> users) {
        this.users = users;
    }
}
