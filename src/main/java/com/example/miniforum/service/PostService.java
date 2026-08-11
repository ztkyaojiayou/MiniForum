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
 * 负责发帖、查询、搜索、标签统计、点赞等核心业务。
 */
@Service
public class PostService {

    private static final int MAX_TAGS = 5;
    private static final int MAX_TAG_LENGTH = 20;

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

    /** 发帖 */
    public PostVO createPost(PostCreateDTO dto, String author) {
        Post post = new Post();
        post.setTitle(dto.getTitle().trim());
        post.setContent(dto.getContent().trim());
        post.setAuthor(author);
        post.setCreatedAt(LocalDateTime.now());
        post.setTags(normalizeTags(dto.getTags()));
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

    /** 查看所有帖子（最新在前） */
    public List<PostVO> getAllPosts(String username) {
        return postRepository.findAll().stream()
                .map(p -> toVO(p, username))
                .collect(Collectors.toList());
    }

    /** 根据 ID 查询帖子，不存在时抛出异常 */
    public PostVO getById(Long id, String username) {
        Post post = getPostOrThrow(id);
        return toVO(post, username);
    }

    /** 分页查询帖子（最新在前），支持按标签筛选 */
    public PageResult<PostVO> getPosts(int page, int size, String tag, String username) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> all = postRepository.findAll();
        if (tag != null && !tag.isBlank()) {
            String t = tag.trim();
            all = all.stream()
                    .filter(p -> p.getTags() != null && p.getTags().contains(t))
                    .collect(Collectors.toList());
        }
        long total = all.size();
        int totalPages = safeSize > 0 ? (int) Math.ceil((double) total / safeSize) : 0;
        int fromIndex = Math.min((safePage - 1) * safeSize, (int) total);
        int toIndex = Math.min(fromIndex + safeSize, (int) total);
        List<PostVO> records = all.isEmpty() ? new ArrayList<>()
                : all.subList(fromIndex, toIndex).stream()
                        .map(p -> toVO(p, username))
                        .collect(Collectors.toList());
        return new PageResult<>(records, total, safePage, safeSize);
    }

    /** 关键字搜索（忽略大小写，标题命中优先于内容命中，最新在前） */
    public List<PostVO> search(String keyword, String username) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>();
        }
        String kw = keyword.trim().toLowerCase();
        return postRepository.findAll().stream()
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

    /** 统计所有标签及其帖子数（按帖子数降序） */
    public List<TagInfo> getAllTags() {
        Map<String, Long> counter = new HashMap<>();
        for (Post p : postRepository.findAll()) {
            if (p.getTags() == null) {
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

    /** 点赞（同一用户对同一帖子只能点赞一次） */
    public PostVO like(Long postId, String username) {
        Post post = getPostOrThrow(postId);
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
        Like like = likeRepository.findByPostIdAndUsername(postId, username)
                .orElseThrow(() -> new BusinessException("你还没有点过赞"));
        likeRepository.delete(like);
        post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        return toVO(post, username);
    }

    private Post getPostOrThrow(Long postId) {
        return postRepository.findById(postId)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在：id=" + postId));
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
