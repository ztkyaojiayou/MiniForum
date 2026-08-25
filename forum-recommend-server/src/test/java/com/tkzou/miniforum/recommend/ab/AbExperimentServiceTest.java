package com.tkzou.miniforum.recommend.ab;

import com.tkzou.miniforum.recommend.config.ConfigService;
import com.tkzou.miniforum.recommend.config.RecConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AB 实验服务单元测试：分桶稳定性 + 实验组走变体配置。
 */
class AbExperimentServiceTest {

    private ConfigService config() {
        return new ConfigService() {
            private final RecConfig cfg = RecConfig.defaults();

            @Override
            public RecConfig current() {
                return cfg;
            }

            @Override
            public void update(RecConfig config) {
            }

            @Override
            public long version() {
                return 1;
            }
        };
    }

    @Test
    void bucket_shouldBeStableForSameUserAndSalt() {
        AbExperimentService ab = new AbExperimentService(config());
        assertEquals(ab.bucket(42L, "exp-1"), ab.bucket(42L, "exp-1"));
        assertEquals(ab.bucket(999L, "layer-a"), ab.bucket(999L, "layer-a"));
    }

    @Test
    void inExperiment_shouldBeStable() {
        AbExperimentService ab = new AbExperimentService(config());
        boolean v = ab.inExperiment(7L, "rec-v1");
        for (int i = 0; i < 10; i++) {
            assertTrue(ab.inExperiment(7L, "rec-v1") == v);
        }
    }

    @Test
    void configFor_shouldReturnDiversityVariantForExperimentGroup() {
        AbExperimentService ab = new AbExperimentService(config());
        RecConfig base = RecConfig.defaults();
        // 找一个命中实验组 B 的用户
        Long inB = null;
        for (long uid = 1; uid <= 1000; uid++) {
            if (ab.inExperiment(uid, "rec-v1")) {
                inB = uid;
                break;
            }
        }
        assertTrue(inB != null, "1000 个用户内应至少有一个命中 50% 的实验组");
        RecConfig variant = ab.configFor("rec-v1", inB);
        assertTrue(variant.getMmrLambda() != base.getMmrLambda(), "实验组应走多样性变体（MMR 参数不同）");
        assertFalse(variant.getMmrLambda() == base.getMmrLambda());
    }

    @Test
    void configFor_shouldReturnBaseForControlGroup() {
        AbExperimentService ab = new AbExperimentService(config());
        Long inA = null;
        for (long uid = 1; uid <= 1000; uid++) {
            if (!ab.inExperiment(uid, "rec-v1")) {
                inA = uid;
                break;
            }
        }
        assertTrue(inA != null);
        assertEquals(ab.configFor("rec-v1", inA).getMmrLambda(), RecConfig.defaults().getMmrLambda());
    }
}
