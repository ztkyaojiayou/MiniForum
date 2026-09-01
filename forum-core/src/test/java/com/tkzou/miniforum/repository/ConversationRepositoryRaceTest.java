package com.tkzou.miniforum.repository;

import com.tkzou.miniforum.entity.Conversation;
import com.tkzou.miniforum.repository.impl.InMemoryConversationRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 私信会话"查找或创建"并发竞态测试（P0-6）
 * <p>
 * 并发 {@link ConversationRepository#findOrCreateByPair} 只应创建一个会话，且所有线程拿到同一个
 * 真实 id——修复原先 findByPair().orElseGet(save) 并发下重复建会话 / MySQL upsert 返回新 id 的脏数据。
 */
class ConversationRepositoryRaceTest {

    @Test
    void concurrentFindOrCreateByPair_shouldCreateSingleConversation() throws Exception {
        InMemoryConversationRepository repo = new InMemoryConversationRepository();
        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Long>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return repo.findOrCreateByPair("alice", "bob").getId();
            }));
        }
        start.countDown();
        List<Long> ids = new ArrayList<>();
        for (Future<Long> f : futures) {
            ids.add(f.get());
        }
        pool.shutdownNow();

        // 所有并发线程拿到同一个会话 id，且只创建一个会话
        Set<Long> distinct = new HashSet<>(ids);
        assertEquals(1, distinct.size());
        assertEquals(1L, repo.count());
        // 再次查找（反向顺序）仍命中同一会话
        Conversation again = repo.findOrCreateByPair("bob", "alice");
        assertEquals(ids.get(0), again.getId());
    }
}
