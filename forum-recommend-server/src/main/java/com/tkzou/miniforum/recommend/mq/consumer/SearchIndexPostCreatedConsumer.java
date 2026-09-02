package com.tkzou.miniforum.recommend.mq.consumer;
import com.tkzou.miniforum.recommend.mq.consumer.PostCreatedConsumer;
import com.tkzou.miniforum.recommend.mq.PostCreatedEvent;

import com.tkzou.miniforum.repository.PostRepository;
import com.tkzou.miniforum.search.SearchIndex;
import com.tkzou.miniforum.recommend.mq.PostCreatedEventBus;
import org.springframework.stereotype.Component;

/**
 * 搜索索引更新订阅者（实现 {@link PostCreatedConsumer}，由 {@link PostCreatedEventBus} 构造器自动注册）
 * <p>
 * 收到发帖事件 → 回源帖子 → {@link SearchIndex#index} 增量索引（标题/内容/标签/话题）。
 * 与 FanoutPostCreatedConsumer 是总线的一路并行消费者（见 {@link PostCreatedEventBus}），
 * @!prod/@prod 通吃（发帖 → 索引，演示同步 / 生产异步）。
 */
@Component
public class SearchIndexPostCreatedConsumer implements PostCreatedConsumer {

    private final SearchIndex searchIndex;
    private final PostRepository postRepository;

    public SearchIndexPostCreatedConsumer(SearchIndex searchIndex, PostRepository postRepository) {
        this.searchIndex = searchIndex;
        this.postRepository = postRepository;
    }

    @Override
    public String name() {
        return "search-index";
    }

    @Override
    public void consume(PostCreatedEvent event) {
        postRepository.findById(event.getPostId()).ifPresent(searchIndex::index);
    }
}