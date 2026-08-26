package com.tkzou.miniforum.recommend.stream;

import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.search.SearchIndex;
import org.springframework.stereotype.Component;

/**
 * 搜索索引更新订阅者（订阅 {@link PostCreatedEventBus}）
 * <p>
 * 收到发帖事件 → 回源帖子 → {@link SearchIndex#index} 增量索引（标题/内容/标签/话题）。
 * 与 FanoutOnPostCreated 一样是总线的一路消费者，@!prod/@prod 通吃（发帖 → 索引，演示同步 / 生产异步）。
 */
@Component
public class SearchIndexUpdater {

    public SearchIndexUpdater(PostCreatedEventBus eventBus, SearchIndex searchIndex, PostRepository postRepository) {
        eventBus.subscribe(event ->
                postRepository.findById(event.getPostId()).ifPresent(searchIndex::index)
        );
    }
}
