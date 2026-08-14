package com.tkzou.miniforum.controller;

import com.tkzou.miniforum.common.Result;
import com.tkzou.miniforum.dto.PageResult;
import com.tkzou.miniforum.dto.PostVO;
import com.tkzou.miniforum.dto.ProfileVO;
import com.tkzou.miniforum.dto.UserCreateDTO;
import com.tkzou.miniforum.dto.UserUpdateDTO;
import com.tkzou.miniforum.entity.User;
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
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final PostService postService;

    public UserController(UserService userService, PostService postService) {
        this.userService = userService;
        this.postService = postService;
    }

    /** 新增用户 */
    @PostMapping
    public ResponseEntity<Result<User>> createUser(@Valid @RequestBody UserCreateDTO dto) {
        User created = userService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(Result.success(created));
    }

    /** 查询单个用户 */
    @GetMapping("/{id}")
    public ResponseEntity<Result<User>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(userService.getUserById(id)));
    }

    /** 个人主页聚合信息：用户资料 + 粉丝数 + 关注数 + 帖子数 */
    @GetMapping("/{id}/profile")
    public ResponseEntity<Result<ProfileVO>> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(Result.success(userService.getProfile(id)));
    }

    /** 查询所有用户 */
    @GetMapping
    public ResponseEntity<Result<List<User>>> getAllUsers() {
        return ResponseEntity.ok(Result.success(userService.getAllUsers()));
    }

    /** 修改用户 */
    @PutMapping("/{id}")
    public ResponseEntity<Result<User>> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO dto) {
        return ResponseEntity.ok(Result.success(userService.updateUser(id, dto)));
    }

    /** 删除用户 */
    @DeleteMapping("/{id}")
    public ResponseEntity<Result<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(Result.success("删除成功", null));
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
