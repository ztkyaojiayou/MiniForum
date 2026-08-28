package com.tkzou.miniforum.recommend.profile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 画像短 TTL 缓存测试（P0-1，profile 域）
 * <p>
 * ① TTL 内第二次读取命中缓存（免重复全量聚合 UserProfileAggregator.build）；② TTL≤0 时禁用缓存（测试/调试可关）。
 * 对齐高并发铁律"能预计算的不实时算"：现算只发生在缓存 miss 时。
 */
class InMemoryUserProfileServiceCacheTest {

    private UserProfileAggregator aggregator;
    private InMemoryUserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        aggregator = mock(UserProfileAggregator.class);
        userProfileService = new InMemoryUserProfileService(aggregator);
        userProfileService.setProfileCacheTtlMs(30_000L); // 默认启用：对齐 application.yml
    }

    @Test
    void userProfile_isCachedWithinTtl() {
        UserProfile p = mock(UserProfile.class);
        when(aggregator.build(1L)).thenReturn(p);
        assertSame(p, userProfileService.userProfile(1L), "首次应现算");
        assertSame(p, userProfileService.userProfile(1L), "TTL 内第二次应命中缓存（同一对象）");
        verify(aggregator, times(1)).build(1L);
    }

    @Test
    void userProfile_cacheDisabledWhenTtlZero() {
        userProfileService.setProfileCacheTtlMs(0L);
        when(aggregator.build(1L)).thenReturn(mock(UserProfile.class));
        userProfileService.userProfile(1L);
        userProfileService.userProfile(1L);
        verify(aggregator, times(2)).build(1L);
    }
}
