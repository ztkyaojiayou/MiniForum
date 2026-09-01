package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.request.ChangePasswordDTO;
import com.tkzou.miniforum.dto.request.ProfileUpdateDTO;
import com.tkzou.miniforum.dto.response.ProfileVO;
import com.tkzou.miniforum.dto.request.UserCreateDTO;
import com.tkzou.miniforum.dto.request.UserUpdateDTO;
import com.tkzou.miniforum.entity.Post;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.BusinessException;
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

    /** 按用户名查询用户（@提及 跳转用） */
    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("用户不存在: " + username));
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

    /**
     * 修改个人资料（昵称 / 简介 / 头像 / 邮箱 / 年龄，传 null 的字段保持不变；用户名不可修改）
     */
    public User updateProfile(Long id, ProfileUpdateDTO dto) {
        User existing = getUserById(id);
        if (dto.getNickname() != null && !dto.getNickname().isBlank()) {
            existing.setNickname(dto.getNickname().trim());
        }
        existing.setBio(dto.getBio());
        existing.setAvatar(dto.getAvatar());
        if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
            existing.setEmail(dto.getEmail().trim());
        }
        if (dto.getAge() != null) {
            existing.setAge(dto.getAge());
        }
        return userRepository.save(existing);
    }

    /** 修改密码：校验旧密码正确后设置新密码 */
    public void changePassword(Long id, ChangePasswordDTO dto) {
        User user = getUserById(id);
        if (!PasswordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BusinessException("旧密码不正确");
        }
        user.setPassword(PasswordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new ResourceNotFoundException("用户不存在: id=" + id);
        }
        userRepository.deleteById(id);
    }
}
