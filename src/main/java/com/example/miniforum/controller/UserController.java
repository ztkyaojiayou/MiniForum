package com.example.miniforum.controller;

import com.example.miniforum.common.Result;
import com.example.miniforum.dto.UserCreateDTO;
import com.example.miniforum.dto.UserUpdateDTO;
import com.example.miniforum.entity.User;
import com.example.miniforum.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 用户管理 REST 接口
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
}
