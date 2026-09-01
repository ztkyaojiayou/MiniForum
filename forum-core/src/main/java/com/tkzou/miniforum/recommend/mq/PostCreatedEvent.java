package com.tkzou.miniforum.recommend.mq;

import java.util.ArrayList;
import java.util.List;

/**
 * 帖子创建事件
 * <p>
 * 发帖落库后发布的事件负载（生产经 Kafka topic "post-created" 异步下发下游，
 * 用于搜索索引 / feed 扇出 / 内容管道 / 推荐冷启动等）。
 */
public class PostCreatedEvent {

    private Long postId;

    private Long authorId;

    private String author;

    private String title;

    private String content;

    private String category;

    private List<String> topics = new ArrayList<>();

    public PostCreatedEvent() {
    }

    public PostCreatedEvent(Long postId, Long authorId, String author, String title,
                            String content, String category, List<String> topics) {
        this.postId = postId;
        this.authorId = authorId;
        this.author = author;
        this.title = title;
        this.content = content;
        this.category = category;
        this.topics = topics == null ? new ArrayList<>() : new ArrayList<>(topics);
    }

    public Long getPostId() {
        return postId;
    }

    public void setPostId(Long postId) {
        this.postId = postId;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public void setAuthorId(Long authorId) {
        this.authorId = authorId;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }
}
