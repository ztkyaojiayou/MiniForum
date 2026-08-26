package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.ChangePasswordDTO;
import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.dto.ProfileUpdateDTO;
import com.tkzou.miniforum.dto.ProfileVO;
import com.tkzou.miniforum.dto.UserBriefVO;
import com.tkzou.miniforum.dto.UserCreateDTO;
import com.tkzou.miniforum.dto.UserUpdateDTO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.exception.BusinessException;
import com.tkzou.miniforum.service.PostService;
import com.tkzou.miniforum.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.List;

/**
 * 用户管理 REST 接口
 */
/**
 * 用户接口（/api/users）
 * <p>
 * 用户 CRUD（管理端，仅 admin）/ 资料查询与修改 / 改密 / 个人主页帖子。
 * 游客可看用户主页与资料读路径；写操作（注册/改密/改资料）需登录，账号管理仅 admin。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PostService postService;

    public UserController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    /** 新增用户（仅管理员；注册走公开的 /api/auth/register） */
    @PostMapping
    public ResponseEntity<Result<User>> createUser(@Valid @RequestBody UserCreateDTO dto,
                                                   HttpSession session) {
        requireAdmin(session);
        User created = userService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(created));
    }

    /** 查询单个用户 */
    @GetMapping("/{id}")
    public ResponseEntity<Result<User>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(userService.getUserById(id)));
    }

    /** 按用户名查询用户（@提及 跳转用） */
    @GetMapping("/by-username/{username}")
    public ResponseEntity<Result<UserBriefVO>> getUserByUsername(@PathVariable String username) {
        return ResponseEntity.ok(Result.success(new UserBriefVO(userService.getUserByUsername(username))));
    }

    /** 个人主页聚合信息：用户资料 + 粉丝数 + 关注数 + 帖子数 */
    @GetMapping("/{id}/profile")
    public ResponseEntity<Result<ProfileVO>> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(userService.getProfile(id)));
    }

    /** 查询所有用户（仅管理员，用户管理弹窗用） */
    @GetMapping
    public ResponseEntity<Result<List<User>>> getAllUsers(HttpSession session) {
        requireAdmin(session);
        return ResponseEntity.ok(Result.success(userService.getAllUsers()));
    }

    /** 修改用户（仅管理员） */
    @PutMapping("/{id}")
    public ResponseEntity<Result<User>> updateUser(@PathVariable Long id,
                                                   @Valid @RequestBody UserUpdateDTO dto,
                                                   HttpSession session) {
        requireAdmin(session);
        return ResponseEntity.ok(Result.success(userService.updateUser(id, dto)));
    }

    /** 修改个人资料（昵称 / 简介 / 头像 / 邮箱 / 年龄，仅本人或管理员可操作） */
    @PutMapping("/{id}/profile")
    public ResponseEntity<Result<User>> updateProfile(@PathVariable Long id,
                                                      @Valid @RequestBody ProfileUpdateDTO dto,
                                                      HttpSession session) {
        ensureSelfOrAdmin(id, session);
        return ResponseEntity.ok(Result.success("资料已更新", userService.updateProfile(id, dto)));
    }

    /** 修改密码（需校验旧密码，仅本人可操作） */
    @PutMapping("/{id}/password")
    public ResponseEntity<Result<Void>> changePassword(@PathVariable Long id,
                                                       @Valid @RequestBody ChangePasswordDTO dto,
                                                       HttpSession session) {
        ensureSelfOrAdmin(id, session);
        userService.changePassword(id, dto);
        return ResponseEntity.ok(Result.success("密码已修改", null));
    }

    /** 仅本人或管理员可操作 */
    private void ensureSelfOrAdmin(Long id, HttpSession session) {
        Long currentId = (Long) session.getAttribute("userId");
        String username = (String) session.getAttribute("username");
        if (currentId == null || (!currentId.equals(id) && !"admin".equals(username))) {
            throw new com.tkzou.miniforum.exception.BusinessException("无权操作其他用户的资料");
        }
    }

    /** 删除用户（仅管理员） */
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deleteUser(@PathVariable Long id, HttpSession session) {
        requireAdmin(session);
        userService.deleteUser(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
    }

    /** 校验管理员权限（账号管理仅管理员可用） */
    private void requireAdmin(HttpSession session) {
        String username = (String) session.getAttribute("username");
        if (!"admin".equals(username)) {
            throw new BusinessException("仅管理员可执行此操作");
        }
    }

    /** 个人主页：某用户的全部已发布帖子（分页，最新在前） */
    @GetMapping("/{id}/posts")
    public ResponseEntity<Result<PageResult<PostVO>>> getUserPosts(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size,
            HttpSession session) {
        // 用户不存在时抛出 404
        userService.getUserById(id);
        String username = (String) session.getAttribute("username");
        return ResponseEntity.ok(Result.success(postService.getPostsByAuthor(id, page, size, username)));
    }
}
