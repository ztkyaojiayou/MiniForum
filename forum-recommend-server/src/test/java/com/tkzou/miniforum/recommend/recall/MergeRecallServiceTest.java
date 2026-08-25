package com.tkzou.miniforum.recommend.recall;

import com.tkzou.miniforum.recommend.config.RecConfig;
import com.tkzou.miniforum.recommend.domain.Candidate;
import com.tkzou.miniforum.recommend.domain.RecallHit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多路召回融合单元测试：去重 + 多路命中融合分更高。
 */
class MergeRecallServiceTest {

    private final MergeRecallService merger = new MergeRecallService();

    @Test
    void merge_shouldDedupeByItemId() {
        List<RecallHit> hits = List.of(
                new RecallHit(1L, 10.0, "hot"),
                new RecallHit(1L, 5.0, "itemcf"),
                new RecallHit(2L, 3.0, "hot"));
        List<Candidate> merged = merger.merge(hits, RecConfig.defaults(), 10);
        assertEquals(2, merged.size(), "同 itemId 多路命中应去重为一条候选");
    }

    @Test
    void merge_shouldFavorMultiChannelHits() {
        List<RecallHit> hits = List.of(
                new RecallHit(1L, 10.0, "hot"),
                new RecallHit(1L, 9.0, "itemcf"),   // item1 两路命中
                new RecallHit(2L, 100.0, "hot"));   // item2 单路高分命中
        List<Candidate> merged = merger.merge(hits, RecConfig.defaults(), 10);
        Candidate item1 = merged.stream().filter(c -> c.getItemId() == 1L).findFirst().orElseThrow();
        Candidate item2 = merged.stream().filter(c -> c.getItemId() == 2L).findFirst().orElseThrow();
        assertTrue(item1.getChannelScores().size() == 2, "item1 应保留两路得分");
        assertTrue(item1.getMergeScore() > item2.getMergeScore(),
                "多路命中（item1）融合分应高于单路高分（item2），实际 " + item1.getMergeScore() + " vs " + item2.getMergeScore());
    }

    @Test
    void merge_shouldRespectTargetSize() {
        List<RecallHit> hits = List.of(
                new RecallHit(1L, 10, "hot"), new RecallHit(2L, 9, "hot"), new RecallHit(3L, 8, "hot"));
        List<Candidate> merged = merger.merge(hits, RecConfig.defaults(), 2);
        assertEquals(2, merged.size());
    }
}
