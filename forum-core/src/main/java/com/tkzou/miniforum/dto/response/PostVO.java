package com.tkzou.miniforum.dto.response;
import lombok.Getter;
import lombok.Setter;

import com.tkzou.miniforum.entity.Post;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 帖子视图对象（返回给前端）
 * <p>
 * 在 {@link Post} 基础上追加点赞数、当前用户是否已点赞、评论数等展示字段，
 * 避免将请求相关的状态写入实体。
 */
// 样板 getter/setter 由 Lombok @Getter @Setter 生成（非核心 POJO；核心实体保留显式以便学习，见第 2 章）
@Getter @Setter
public class PostVO {

    private Long id;

    private String title;

    private String content;

    private String author;

    /** 作者用户 ID（用于个人主页跳转） */
    private Long authorId;

    private LocalDateTime createdAt;

    private List<String> tags = new ArrayList<>();

    /** 话题列表（内容中自动提取的 #话题#） */
    private List<String> topics = new ArrayList<>();

    /** 分类（固定分类之一，空值兜底为"其他"） */
    private String category;

    /** 状态：DRAFT / PUBLISHED */
    private String status;

    /** 点赞总数 */
    private long likeCount;

    /** 当前登录用户是否已点赞 */
    private boolean likedByMe;

    /** 当前登录用户是否已收藏 */
    private boolean favoritedByMe;

    /** 评论总数 */
    private long commentCount;

    /** 阅读量 */
    private long viewCount;

    /** 转发原帖 ID（null = 原创帖） */
    private Long originalPostId;

    /** 转发原帖作者用户名 */
    private String originalAuthor;

    /** 转发原帖标题（用于"转发泡"展示） */
    private String originalTitle;

    /** 转发原帖内容片段（用于"转发泡"展示） */
    private String originalContent;

    /** 转发数（统计转发该帖的帖子数） */
    private long repostCount;

    public PostVO() {
    }

    public PostVO(Post post) {
        this.id = post.getId();
        this.title = post.getTitle();
        this.content = post.getContent();
        this.author = post.getAuthor();
        this.authorId = post.getAuthorId();
        this.createdAt = post.getCreatedAt();
        this.tags = post.getTags() == null ? new ArrayList<>() : new ArrayList<>(post.getTags());
        this.topics = post.getTopics() == null ? new ArrayList<>() : new ArrayList<>(post.getTopics());
        this.status = post.getStatus() == null ? null : post.getStatus().name(); // 枚举 → 字符串展示（前端契约不变）
        this.originalPostId = post.getOriginalPostId();
        this.originalAuthor = post.getOriginalAuthor();
    }

}
