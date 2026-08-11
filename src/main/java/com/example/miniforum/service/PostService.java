package com.example.miniforum.service;

import com.example.miniforum.dto.PageResult;
import com.example.miniforum.dto.PostCreateDTO;
import com.example.miniforum.dto.PostVO;
import com.example.miniforum.dto.TagInfo;
import com.example.miniforum.entity.Like;
import com.example.miniforum.entity.Post;
import com.example.miniforum.exception.BusinessException;
import com.example.miniforum.exception.ResourceNotFoundException;
import com.example.miniforum.repository.CommentRepository;
import com.example.miniforum.repository.LikeRepository;
import com.example.miniforum.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 帖子服务
 * <p>
 * 负责发帖、查询、搜索、标签统计、点赞、个人主页、草稿管理等核心业务。
 */
@Service
public class PostService {

    private static final int MAX_TAGS = 5;
    private static final int MAX_TAG_LENGTH = 20;
    /** 管理员用户名（可编辑/删除任意帖子） */
    private static final String ADMIN_USERNAME = "admin";

    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;

    public PostService(PostRepository postRepository,
                       LikeRepository likeRepository,
                       CommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.commentRepository = commentRepository;
    }

    /** 发帖（publish=false 时存为草稿） */
    public PostVO createPost(PostCreateDTO dto, String author, Long authorId) {
        Post post = new Post();
        post.setTitle(dto.getTitle().trim());
        post.setContent(dto.getContent().trim());
        post.setAuthor(author);
        post.setAuthorId(authorId);
        post.setCreatedAt(LocalDateTime.now());
        post.setTags(normalizeTags(dto.getTags()));
        post.setStatus(dto.getPublish() ? Post.STATUS_PUBLISHED : Post.STATUS_DRAFT);
        Post saved = postRepository.save(post);
        return toVO(saved, author);
    }

    /**
     * 规范化并校验标签：去空白、去重、最多 5 个、每个不超过 20 字符
     */
    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String raw : tags) {
            if (raw == null) {
                continue;
            }
            String tag = raw.trim();
            if (tag.isEmpty()) {
                continue;
            }
            if (tag.length() > MAX_TAG_LENGTH) {
                throw new BusinessException("单个标签不能超过 " + MAX_TAG_LENGTH + " 个字符");
            }
            if (!result.contains(tag)) {
                result.add(tag);
            }
        }
        if (result.size() > MAX_TAGS) {
            throw new BusinessException("最多添加 " + MAX_TAGS + " 个标签");
        }
        return result;
    }

    /** 查看所有已发布帖子（最新在前，草稿不出现在公开列表） */
    public List<PostVO> getAllPosts(String username) {
        return postRepository.findAll().stream()
                .filter(this::isPublished)
                .map(p -> toVO(p, username))
                .collect(Collectors.toList());
    }

    /** 根据 ID 查询帖子，不存在时抛出异常；草稿仅作者本人/管理员可见 */
    public PostVO getById(Long id, String username) {
        Post post = getPostOrThrow(id);
        if (isDraft(post) && !isOwnerOrAdmin(post, username)) {
            throw new ResourceNotFoundException("帖子不存在：id=" + id);
        }
        return toVO(post, username);
    }

    /** 分页查询已发布帖子（最新在前），支持按标签筛选 */
    public PageResult<PostVO> getPosts(int page, int size, String tag, String username) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> all = postRepository.findAll().stream()
                .filter(this::isPublished)
                .collect(Collectors.toList());
        if (tag != null && !tag.isBlank()) {
            String t = tag.trim();
            all = all.stream()
                    .filter(p -> p.getTags() != null && p.getTags().contains(t))
                    .collect(Collectors.toList());
        }
        return paginate(all, safePage, safeSize, username);
    }

    /** 个人主页：某用户的全部已发布帖子（分页，最新在前） */
    public PageResult<PostVO> getPostsByAuthor(Long authorId, int page, int size, String username) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> all = postRepository.findByAuthorId(authorId).stream()
                .filter(this::isPublished)
                .collect(Collectors.toList());
        return paginate(all, safePage, safeSize, username);
    }

    /** 我的文章：当前用户自己的文章（默认全部，可按 status=DRAFT/PUBLISHED 过滤，分页） */
    public PageResult<PostVO> getMyPosts(String username, String status, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> all = postRepository.findAll().stream()
                .filter(p -> p.getAuthor().equals(username))
                .filter(p -> status == null || status.isBlank() || status.equals(p.getStatus()))
                .collect(Collectors.toList());
        return paginate(all, safePage, safeSize, username);
    }

    /** 更新帖子（仅作者本人/管理员；publish=true 发布，false 存为草稿） */
    public PostVO updatePost(Long id, PostCreateDTO dto, String username, boolean publish) {
        Post post = getPostOrThrow(id);
        if (!isOwnerOrAdmin(post, username)) {
            throw new BusinessException("只能编辑自己发布的帖子");
        }
        post.setTitle(dto.getTitle().trim());
        post.setContent(dto.getContent().trim());
        post.setTags(normalizeTags(dto.getTags()));
        post.setStatus(publish ? Post.STATUS_PUBLISHED : Post.STATUS_DRAFT);
        return toVO(postRepository.save(post), username);
    }

    /** 删除帖子（仅作者本人/管理员，级联清理评论与点赞） */
    public void deletePost(Long id, String username) {
        Post post = getPostOrThrow(id);
        if (!isOwnerOrAdmin(post, username)) {
            throw new BusinessException("只能删除自己发布的帖子");
        }
        postRepository.deleteById(id);
        commentRepository.deleteByPostId(id);
        likeRepository.deleteByPostId(id);
    }

    /** 关键字搜索（忽略大小写，标题命中优先于内容命中，最新在前，不含草稿） */
    public List<PostVO> search(String keyword, String username) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        String kw = keyword.trim().toLowerCase();
        return postRepository.findAll().stream()
                .filter(this::isPublished)
                .filter(p -> (p.getTitle() != null && p.getTitle().toLowerCase().contains(kw))
                        || (p.getContent() != null && p.getContent().toLowerCase().contains(kw)))
                .sorted((a, b) -> {
                    boolean aTitle = a.getTitle() != null && a.getTitle().toLowerCase().contains(kw);
                    boolean bTitle = b.getTitle() != null && b.getTitle().toLowerCase().contains(kw);
                    if (aTitle != bTitle) {
                        return aTitle ? -1 : 1;
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(p -> toVO(p, username))
                .collect(Collectors.toList());
    }

    /** 统计所有标签及其已发布帖子数（按帖子数降序） */
    public List<TagInfo> getAllTags() {
        Map<String, Long> counter = new HashMap<>();
        for (Post p : postRepository.findAll()) {
            if (!isPublished(p) || p.getTags() == null) {
                continue;
            }
            for (String tag : p.getTags()) {
                counter.merge(tag, 1L, Long::sum);
            }
        }
        return counter.entrySet().stream()
                .map(e -> new TagInfo(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .collect(Collectors.toList());
    }

    /** 点赞（同一用户对同一帖子只能点赞一次，草稿不可点赞） */
    public PostVO like(Long postId, String username) {
        Post post = getPostOrThrow(postId);
        if (isDraft(post)) {
            throw new BusinessException("草稿不能点赞");
        }
        if (likeRepository.findByPostIdAndUsername(postId, username).isPresent()) {
            throw new BusinessException("你已经点过赞了");
        }
        Like like = new Like();
        like.setPostId(postId);
        like.setUsername(username);
        like.setCreatedAt(LocalDateTime.now());
        likeRepository.save(like);
        post.setLikeCount(post.getLikeCount() + 1);
        return toVO(post, username);
    }

    /** 取消点赞 */
    public PostVO unlike(Long postId, String username) {
        Post post = getPostOrThrow(postId);
        if (isDraft(post)) {
            throw new BusinessException("草稿不能点赞");
        }
        Like like = likeRepository.findByPostIdAndUsername(postId, username)
                .orElseThrow(() -> new BusinessException("你还没有点过赞"));
        likeRepository.delete(like);
        post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        return toVO(post, username);
    }

    /** 统一分页逻辑 */
    private PageResult<PostVO> paginate(List<Post> all, int page, int size, String username) {
        long total = all.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        int fromIndex = Math.min((page - 1) * size, (int) total);
        int toIndex = Math.min(fromIndex + size, (int) total);
        List<PostVO> records = all.isEmpty() ? new ArrayList<>()
                : all.subList(fromIndex, toIndex).stream()
                        .map(p -> toVO(p, username))
                        .collect(Collectors.toList());
        return new PageResult<>(records, total, page, size);
    }

    private Post getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在：id=" + postId));
    }

    private boolean isPublished(Post p) {
        return Post.STATUS_PUBLISHED.equals(p.getStatus());
    }

    private boolean isDraft(Post p) {
        return Post.STATUS_DRAFT.equals(p.getStatus());
    }

    private boolean isOwnerOrAdmin(Post p, String username) {
        return p.getAuthor().equals(username) || ADMIN_USERNAME.equals(username);
    }

    /** 转换为视图对象，附带点赞数、当前用户点赞状态与评论数 */
    public PostVO toVO(Post post, String username) {
        PostVO vo = new PostVO(post);
        vo.setLikeCount(post.getLikeCount());
        vo.setLikedByMe(username != null
                && likeRepository.findByPostIdAndUsername(post.getId(), username).isPresent());
        vo.setCommentCount(commentRepository.countByPostId(post.getId()));
        return vo;
    }
}
