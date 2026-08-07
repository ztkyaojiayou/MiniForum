package com.example.usermanagement.service;

import com.example.usermanagement.dto.PostCreateDTO;
import com.example.usermanagement.entity.Post;
import com.example.usermanagement.repository.PostRepository;
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
}
