package com.prarit.ratelimiter.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for RateLimiterService.
 *
 * We mock StringRedisTemplate so no real Redis is needed.
 * This makes tests fast (milliseconds) and runnable in CI with no dependencies.
 *
 * Same Mockito pattern as Project 1 — mock dependencies, test real logic.
 */
@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private RateLimiterService rateLimiterService;

    @BeforeEach
    void setUp() {
        // Tell the mock: when opsForZSet() is called, return our mock ZSetOperations
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    }

    @Test
    @DisplayName("Should ALLOW request when count is below limit")
    void isAllowed_WhenBelowLimit_ReturnsTrue() {
        // ARRANGE: mock Redis to say there are 5 requests in the window
        when(zSetOperations.size(anyString())).thenReturn(5L);

        // ACT: check with limit=10
        boolean result = rateLimiterService.isAllowed("192.168.1.1", 10, 60);

        // ASSERT: 5 < 10 → allowed
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should BLOCK request when count equals limit")
    void isAllowed_WhenAtLimit_ReturnsFalse() {
        // ARRANGE: mock Redis to say there are exactly 10 requests (the limit)
        when(zSetOperations.size(anyString())).thenReturn(10L);

        // ACT
        boolean result = rateLimiterService.isAllowed("192.168.1.1", 10, 60);

        // ASSERT: 10 >= 10 → blocked
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should BLOCK request when count exceeds limit")
    void isAllowed_WhenOverLimit_ReturnsFalse() {
        // ARRANGE: mock Redis to say there are 15 requests (over limit of 10)
        when(zSetOperations.size(anyString())).thenReturn(15L);

        // ACT
        boolean result = rateLimiterService.isAllowed("192.168.1.1", 10, 60);

        // ASSERT: 15 >= 10 → blocked
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Should ALLOW first ever request (Redis returns null)")
    void isAllowed_WhenFirstRequest_ReturnsTrue() {
        // ARRANGE: null simulates key not existing in Redis yet
        when(zSetOperations.size(anyString())).thenReturn(null);

        // ACT
        boolean result = rateLimiterService.isAllowed("192.168.1.1", 10, 60);

        // ASSERT: null → treated as 0 → allowed
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Should always call removeRangeByScore to clean expired entries")
    void isAllowed_AlwaysCleansExpiredEntries() {
        when(zSetOperations.size(anyString())).thenReturn(1L);

        rateLimiterService.isAllowed("192.168.1.1", 10, 60);

        // Verify the sliding window cleanup ALWAYS runs
        verify(zSetOperations, times(1))
            .removeRangeByScore(anyString(), eq(0.0), anyDouble());
    }

    @Test
    @DisplayName("Should always add current request to Redis")
    void isAllowed_AlwaysAddsCurrentRequest() {
        when(zSetOperations.size(anyString())).thenReturn(1L);

        rateLimiterService.isAllowed("192.168.1.1", 10, 60);

        // Verify the current request was added to the ZSet
        verify(zSetOperations, times(1))
            .add(anyString(), anyString(), anyDouble());
    }

    @Test
    @DisplayName("Should use different Redis keys for different clients")
    void isAllowed_UsesSeparateKeysPerClient() {
        when(zSetOperations.size(anyString())).thenReturn(1L);

        rateLimiterService.isAllowed("client-A", 10, 60);
        rateLimiterService.isAllowed("client-B", 10, 60);

        // Verify size() was called twice with DIFFERENT keys
        verify(zSetOperations, times(1))
            .size("rate_limit:client-A");
        verify(zSetOperations, times(1))
            .size("rate_limit:client-B");
    }
}
