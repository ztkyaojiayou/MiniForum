package com.tkzou.miniforum.recommend.stream.impl;
import com.tkzou.miniforum.recommend.stream.PostCreatedSubscriber;
import com.tkzou.miniforum.recommend.stream.PostCreatedEvent;

import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.search.SearchIndex;
import org.springframework.stereotype.Component;

/**
 * 搜索索引更新订阅者（实现 {@link PostCreatedSubscriber}，由 {@link PostCreatedSubscriberRegistrar} 统一注册）
 * <p>
 * 收到发帖事件 → 回源帖子 → {@link SearchIndex#index} 增量索引（标题/内容/标签/话题）。
 * 与 FanoutOnPostCreated 是总线的一路并行消费者（见 {@link PostCreatedSubscriberRegistrar}），
 * @!prod/@prod 通吃（发帖 → 索引，演示同步 / 生产异步）。
 */
@Component
public class SearchIndexUpdater implements PostCreatedSubscriber {

    private final SearchIndex searchIndex;
    private final PostRepository postRepository;

    public SearchIndexUpdater(SearchIndex searchIndex, PostRepository postRepository) {
        this.searchIndex = searchIndex;
        this.postRepository = postRepository;
    }

    @Override
    public String name() {
        return "search-index";
    }

    @Override
    public void onPostCreated(PostCreatedEvent event) {
        postRepository.findById(event.getPostId()).ifPresent(searchIndex::index);
    }
}