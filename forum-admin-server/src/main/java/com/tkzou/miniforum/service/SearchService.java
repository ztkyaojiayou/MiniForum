package com.tkzou.miniforum.service;

import com.tkzou.miniforum.dto.response.PostVO;
import com.tkzou.miniforum.dto.response.SearchResultVO;
import com.tkzou.miniforum.dto.response.UserBriefVO;
import com.tkzou.miniforum.entity.User;
import com.tkzou.miniforum.repository.SearchRecordRepository;
import com.tkzou.miniforum.repository.UserRepository;
import com.tkzou.miniforum.recommend.behavior.BehaviorLogger;
import com.tkzou.miniforum.recommend.behavior.BehaviorType;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 综合搜索服务
 * <p>
 * 同时搜索帖子（标题/内容/标签/话题）与用户（用户名/昵称），
 * 并记录搜索词热度，供热搜榜合并展示。
 */
@Service
public class SearchService {

    private final PostService postService;
    private final UserRepository userRepository;
    private final SearchRecordRepository searchRecordRepository;
    private final BehaviorLogger behaviorLogger;

    public SearchService(PostService postService,
                         UserRepository userRepository,
                         SearchRecordRepository searchRecordRepository,
                         BehaviorLogger behaviorLogger) {
        this.postService = postService;
        this.userRepository = userRepository;
        this.searchRecordRepository = searchRecordRepository;
        this.behaviorLogger = behaviorLogger;
    }

    /** 综合搜索：帖子 + 用户，并记录搜索词 */
    public SearchResultVO search(String keyword, String username) {
        if (keyword == null || keyword.isBlank()) {
            return new SearchResultVO();
        }
        String kw = keyword.trim();
        recordKeyword(kw);
        // 记录用户级搜索行为（画像信号；生产形态进 Kafka）
        userRepository.findByUsername(username)
                .ifPresent(u -> behaviorLogger.log(u.getId(), null, BehaviorType.SEARCH, "POST", null));
        List<PostVO> posts = postService.search(kw, username);
        String lower = kw.toLowerCase();
        List<UserBriefVO> users = userRepository.findAll().stream()
                .filter(u -> matchesUser(u, lower))
                .map(UserBriefVO::new)
                .collect(Collectors.toList());
        return new SearchResultVO(posts, users);
    }

    /** 用户是否命中：用户名或昵称包含关键词（忽略大小写） */
    private boolean matchesUser(User u, String lower) {
        if (u.getUsername() != null && u.getUsername().toLowerCase().contains(lower)) {
            return true;
        }
        return u.getNickname() != null && u.getNickname().toLowerCase().contains(lower);
    }

    /** 记录一次搜索：原子累加次数（不存在则新建 count=1）；多实例下热搜计数不丢不重 */
    private void recordKeyword(String keyword) {
        searchRecordRepository.incrementKeyword(keyword);
    }
}
