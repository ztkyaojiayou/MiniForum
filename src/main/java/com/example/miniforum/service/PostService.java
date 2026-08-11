package com.example.miniforum.service;

import com.example.miniforum.dto.PageResult;
import com.example.miniforum.dto.PostCreateDTO;
import com.example.miniforum.entity.Post;
import com.example.miniforum.exception.ResourceNotFoundException;
import com.example.miniforum.repository.PostRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子服务
 */
@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /** 发帖 */
    public Post createPost(PostCreateDTO dto, String author) {
        Post post = new Post();
        post.setTitle(dto.getTitle().trim());
        post.setContent(dto.getContent().trim());
        post.setAuthor(author);
        post.setCreatedAt(LocalDateTime.now());
        return postRepository.save(post);
    }

    /** 查看所有帖子（最新在前） */
    public List<Post> getAllPosts() {
        return postRepository.findAll();
    }

    /** 根据 ID 查询帖子，不存在时抛出异常 */
    public Post getById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("帖子不存在：id=" + id));
    }

    /** 分页查询帖子（最新在前） */
    public PageResult<Post> getPosts(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        List<Post> all = postRepository.findAll();
        long total = all.size();
        int totalPages = safeSize > 0 ? (int) Math.ceil((double) total / safeSize) : 0;
        int fromIndex = Math.min((safePage - 1) * safeSize, (int) total);
        int toIndex = Math.min(fromIndex + safeSize, (int) total);
        List<Post> records = all.isEmpty() ? List.of() : all.subList(fromIndex, toIndex);
        return new PageResult<>(records, total, safePage, safeSize);
    }
}
