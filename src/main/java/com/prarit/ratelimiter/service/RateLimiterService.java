package com.prarit.ratelimiter.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * THE BRAIN OF THE RATE LIMITER.
 *
 * Implements the Sliding Window algorithm using Redis Sorted Sets (ZSet).
 *
 * WHY SORTED SETS?
 * A Redis Sorted Set stores members with a numeric SCORE.
 * We store each request as a member, with the TIMESTAMP as the score.
 * This lets us:
 *   - Add a new request in O(log N)
 *   - Remove all requests older than our window in O(log N)
 *   - Count current requests in O(1)
 *
 * THE ALGORITHM (4 steps, every request):
 *   1. Get current timestamp in milliseconds
 *   2. Remove all entries with timestamp < (now - windowMs)  ← old requests fall off
 *   3. Add current request to the sorted set
 *   4. Count total entries — if > limit → BLOCKED
 *
 * REDIS KEY FORMAT:
 *   rate_limit:{clientId}
 *   e.g. "rate_limit:192.168.1.1"  or  "rate_limit:user-uuid-123"
 *
 * THREAD SAFETY:
 * StringRedisTemplate is thread-safe. All operations go through
 * Lettuce's single shared connection with multiplexing.
 * No synchronisation needed on our side.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterService {

    /**
     * StringRedisTemplate — Spring's high-level Redis client.
     * Handles serialisation/deserialisation automatically.
     * Uses String keys and String values (sufficient for our use case).
     */
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "rate_limit:";

    /**
     * Checks if the client is within their rate limit.
     *
     * @param clientId      Unique identifier for the client (IP address or user ID)
     * @param limit         Maximum requests allowed in the window
     * @param windowSeconds Duration of the sliding window in seconds
     * @return true if request is ALLOWED, false if BLOCKED
     */
    public boolean isAllowed(String clientId, int limit, int windowSeconds) {
        String key = KEY_PREFIX + clientId;

        // Current time in milliseconds (used as the ZSet score)
        long nowMs = Instant.now().toEpochMilli();

        // Window start = current time minus window duration
        // Any request with timestamp < windowStart is "expired" and gets removed
        long windowStartMs = nowMs - (windowSeconds * 1000L);

        // Get the ZSet operations interface from StringRedisTemplate
        ZSetOperations<String, String> zSetOps = redisTemplate.opsForZSet();

        // ── STEP 1: Remove expired entries ───────────────────────────────────
        // ZREMRANGEBYSCORE key 0 windowStartMs
        // Removes all requests that are older than our window.
        // This is what makes it a SLIDING window — old data falls off naturally.
        zSetOps.removeRangeByScore(key, 0, windowStartMs);

        // ── STEP 2: Add current request ──────────────────────────────────────
        // ZADD key nowMs "unique-member"
        // Each request needs a unique member — we use UUID.
        // The score IS the timestamp, so we can range-query by time.
        // Why UUID? If two requests arrive at the exact same millisecond,
        // duplicate scores are fine in ZSet — members must be unique.
        String requestId = UUID.randomUUID().toString();
        zSetOps.add(key, requestId, nowMs);

        // ── STEP 3: Set expiry on the key ────────────────────────────────────
        // Auto-delete the key after the window expires.
        // Without this, idle clients accumulate keys in Redis forever.
        redisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);

        // ── STEP 4: Count requests in current window ──────────────────────────
        // ZCARD key → returns number of members in the sorted set
        Long requestCount = zSetOps.size(key);
        long count = (requestCount != null) ? requestCount : 0;

        log.debug("Rate limit check — client: {}, count: {}/{}, window: {}s",
                clientId, count, limit, windowSeconds);

        // ALLOWED if count is within limit, BLOCKED if it exceeds it
        boolean allowed = count <= limit;

        if (!allowed) {
            log.warn("Rate limit EXCEEDED — client: {}, count: {}/{}", clientId, count, limit);
        }

        return allowed;
    }

    /**
     * Returns how many requests the client has made in the current window.
     * Used by the controller to send informational headers to the client.
     *
     * @param clientId      Client identifier
     * @param windowSeconds Window duration
     * @return Current request count
     */
    public long getCurrentCount(String clientId, int windowSeconds) {
        String key = KEY_PREFIX + clientId;
        long windowStartMs = Instant.now().toEpochMilli() - (windowSeconds * 1000L);

        // Remove expired entries first to get accurate count
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStartMs);

        Long count = redisTemplate.opsForZSet().size(key);
        return count != null ? count : 0;
    }
}
