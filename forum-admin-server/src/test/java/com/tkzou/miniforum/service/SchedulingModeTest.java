package com.tkzou.miniforum.service;

import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 调度模式守卫测试（P2-5）
 * <p>
 * app.scheduling.mode=xxl（生产，由 XXL-Job 派发）时，@Scheduled 包装必须空转（防与 XXL-Job 双跑）；
 * doXxx 业务方法是唯一执行入口（XXL-Job handler 调用）。演示（local）走 doXxx 与现状一致。
 */
class SchedulingModeTest {

    @Test
    void simulate_xxlMode_noops_butDoSimulateRuns() {
        PostService postService = mock(PostService.class);
        CommentService commentService = mock(CommentService.class);
        UserRepository userRepository = mock(UserRepository.class);
        PostRepository postRepository = mock(PostRepository.class);
        SimulatedActivityService svc = new SimulatedActivityService(postService, commentService,
                userRepository, postRepository);
        ReflectionTestUtils.setField(svc, "enabled", true);
        ReflectionTestUtils.setField(svc, "schedulingMode", "xxl"); // 生产：由 XXL-Job 派发
        ReflectionTestUtils.setField(svc, "postsPerTick", 1);
        ReflectionTestUtils.setField(svc, "interactionsPerTick", 0);
        User sim = new User();
        sim.setId(1L);
        sim.setUsername("sim");
        when(userRepository.findAll()).thenReturn(List.of(sim));

        svc.simulate(); // @Scheduled 定时触发：xxl 模式应空转（不执行业务）
        verify(postService, never()).createPost(any(), any(), any());

        svc.doSimulate(); // XXL-Job handler 入口：执行实际逻辑
        verify(postService, atLeastOnce()).createPost(any(), any(), any());
    }
}
