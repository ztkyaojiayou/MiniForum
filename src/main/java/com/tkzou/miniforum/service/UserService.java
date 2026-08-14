package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.ProfileVO;
import com.tkzou.miniforum.dto.UserCreateDTO;
import com.tkzou.miniforum.dto.UserUpdateDTO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.DuplicateUsernameException;
import com.tkzou.miniforum.exception.ResourceNotFoundException;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.util.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FollowService followService;

    public UserService(UserRepository userRepository,
                       PostRepository postRepository,
                       FollowService followService) {
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.followService = followService;
    }

    public User createUser(UserCreateDTO dto) {
        if (userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new DuplicateUsernameException("用户名已存在: " + dto.getUsername());
        }
        User user = new User();
        user.setUsername(dto.getUsername());
        user.setEmail(dto.getEmail());
        user.setPassword(PasswordEncoder.encode(dto.getPassword()));
        user.setAge(dto.getAge());
        return userRepository.save(user);
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: id=" + id));
    }

    /** 个人主页聚合信息：用户资料 + 粉丝数 + 关注数 + 已发布帖子数 */
    public ProfileVO getProfile(Long id) {
        User user = getUserById(id);
        ProfileVO vo = new ProfileVO(user);
        vo.setFollowerCount(followService.countFollowers(id));
        vo.setFollowingCount(followService.countFollowing(id));
        vo.setPostCount(postRepository.findByAuthorId(id).stream()
                .filter(p -> Post.STATUS_PUBLISHED.equals(p.getStatus()))
                .count());
        return vo;
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public User updateUser(Long id, UserUpdateDTO dto) {
        User existing = getUserById(id);
        // 用户名不允许修改，若修改了用户名则校验唯一性
        if (!existing.getUsername().equals(dto.getUsername())
                && userRepository.findByUsername(dto.getUsername()).isPresent()) {
            throw new DuplicateUsernameException("用户名已存在: " + dto.getUsername());
        }
        existing.setUsername(dto.getUsername());
        existing.setEmail(dto.getEmail());
        existing.setPassword(PasswordEncoder.encode(dto.getPassword()));
        existing.setAge(dto.getAge());
        return userRepository.save(existing);
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("用户不存在: id=" + id);
        }
        userRepository.deleteById(id);
    }
}
