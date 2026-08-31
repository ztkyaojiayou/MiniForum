package com.tkzou.miniforum.dto;

import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.repository.CommentRepository;
import com.tkzou.miniforum.repository.FavoriteRepository;
import com.tkzou.miniforum.repository.LikeRepository;
import com.tkzou.miniforum.repository.PostRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帖子视图装配器（共享域）
 * <p>
 * 把 {@link Post} 装配为 {@link PostVO}（点赞数 / 收藏状态 / 评论数 / 转发数 / 分类 / 转发泡）。
 * 同时承载固定分类常量与 {@link #resolveCategory(Post)} 兜底逻辑。
 * <p>
 * 从原 {@code PostService} 抽出：业务侧（admin）与推荐侧（recommend）都要把帖子转 VO，
 * 抽到共享域避免 recommend → admin 的依赖环。
 */
@Component
public class PostAssembler {

    /** 固定分类（不含"全部动态"虚拟分类，按左栏展示顺序） */
    public static final List<String> CATEGORIES = List.of(
            "科技", "数码", "游戏", "娱乐", "体育", "财经", "汽车", "时事", "教育", "生活", "其他");
    /** 默认分类（分类为空或旧数据时的兜底值） */
    public static final String CATEGORY_DEFAULT = "其他";
    /** 分类图标映射 */
    private static final Map<String, String> CATEGORY_ICONS = createCategoryIcons();

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FavoriteRepository favoriteRepository;

    public PostAssembler(PostRepository postRepository,
                         LikeRepository likeRepository,
                         CommentRepository commentRepository,
                         FavoriteRepository favoriteRepository) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
        this.favoriteRepository = favoriteRepository;
    }

    /** 解析帖子分类：旧数据/空分类兜底为"其他"，保证筛选一致 */
    public String resolveCategory(Post p) {
        String c = p.getCategory();
        return (c == null || c.isBlank()) ? CATEGORY_DEFAULT : c;
    }

    /** 转换为视图对象，附带点赞数、收藏状态、当前用户点赞状态、评论数与转发数 */
    public PostVO toVO(Post post, String username) {
        PostVO vo = new PostVO(post);
        vo.setLikeCount(post.getLikeCount());
        vo.setViewCount(post.getViewCount());
        vo.setLikedByMe(username != null
                && likeRepository.findByPostIdAndUsername(post.getId(), username).isPresent());
        vo.setFavoritedByMe(username != null
                && favoriteRepository.findByPostIdAndUsername(post.getId(), username).isPresent());
        vo.setCommentCount(commentRepository.countByPostId(post.getId()));
        vo.setCategory(resolveCategory(post));
        vo.setRepostCount(countReposts(post.getId()));
        // 转发泡：补充原帖标题与内容片段
        if (post.getOriginalPostId() != null) {
            postRepository.findById(post.getOriginalPostId()).ifPresent(orig -> {
                vo.setOriginalTitle(orig.getTitle());
                String origContent = orig.getContent() == null ? "" : orig.getContent();
                vo.setOriginalContent(origContent.length() > 100 ? origContent.substring(0, 100) + "…" : origContent);
            });
        }
        return vo;
    }

    /**
     * 统计某帖被转发的次数（仅统计可见的转发帖）。
     * 口径 = 【直接转发数】：只数 {@code originalPostId == 本帖} 的帖子（转发链 A←B←C 时，A 只算 B 直接转的一次）；
     * 不含"链式转发总量"（折叠到根的递归回溯，本项目不做）。
     */
    private long countReposts(Long postId) {
        return postRepository.findAll().stream()
                .filter(p -> postId.equals(p.getOriginalPostId()) && isVisible(p))
                .count();
    }

    /** 公开可见：已发布且未删除 */
    private boolean isVisible(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus()) && !p.isDeleted();
    }

    private static Map<String, String> createCategoryIcons() {
        Map<String, String> icons = new HashMap<>();
        icons.put("科技", "💻");
        icons.put("数码", "📱");
        icons.put("游戏", "🎮");
        icons.put("娱乐", "🎬");
        icons.put("体育", "⚽");
        icons.put("财经", "💰");
        icons.put("汽车", "🚗");
        icons.put("时事", "📰");
        icons.put("教育", "📚");
        icons.put("生活", "🏠");
        icons.put("其他", "✨");
        return icons;
    }
}
