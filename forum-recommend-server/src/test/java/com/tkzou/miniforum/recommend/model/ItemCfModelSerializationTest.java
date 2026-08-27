package com.tkzou.miniforum.recommend.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ItemCF 模型序列化 round-trip 测试（P2-3）
 * <p>
 * 离线构建 → getSimMap 序列化 → Redis → 反序列化 from(map)，验证模型不丢失。
 */
class ItemCfModelSerializationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTrip_preservesModel() throws Exception {
        ItemCfModel model = new ItemCfModel();
        model.putSimilarities(1L, List.of(new ItemCfModel.SimilarItem(2L, 0.8), new ItemCfModel.SimilarItem(3L, 0.6)));
        model.putSimilarities(2L, List.of(new ItemCfModel.SimilarItem(1L, 0.8)));

        String json = mapper.writeValueAsString(model.getSimMap());
        Map<Long, List<ItemCfModel.SimilarItem>> map = mapper.readValue(
                json, new TypeReference<Map<Long, List<ItemCfModel.SimilarItem>>>() {});
        ItemCfModel restored = ItemCfModel.from(map);

        assertEquals(model.size(), restored.size(), "反序列化后物品数应一致");
        assertEquals(2, restored.topSimilar(1L, 5).size(), "TopK 相似列表应完整");
        assertEquals(0.8, restored.similarity(1L, 2L), 1e-9, "相似度应保留");
    }

    @Test
    void empty_roundTrips() throws Exception {
        String json = mapper.writeValueAsString(ItemCfModel.empty().getSimMap());
        ItemCfModel restored = ItemCfModel.from(
                mapper.readValue(json, new TypeReference<Map<Long, List<ItemCfModel.SimilarItem>>>() {}));
        assertEquals(0, restored.size());
    }
}
