package org.codeart.ratelimit.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.config.RateLimitProperties;
import org.codeart.ratelimit.dto.RateLimitInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

/**
 * Redis-only rate limiter using Lua script for Sliding Window Log algorithm.
 * 
 * <p>
 * How it works:
 * </p>
 * <ol>
 * <li>Store each request timestamp in a sorted set (ZADD)</li>
 * <li>Remove expired entries outside the window (ZREMRANGEBYSCORE)</li>
 * <li>Count entries in the window (ZCARD)</li>
 * <li>If count >= limit, reject; otherwise add new entry</li>
 * </ol>
 * 
 * <p>
 * Pros:
 * </p>
 * <ul>
 * <li>Accurate sliding window - no boundary burst issues</li>
 * <li>Atomic operation via Lua script</li>
 * <li>Precise rate limiting</li>
 * </ul>
 * 
 * <p>
 * Cons:
 * </p>
 * <ul>
 * <li>Higher memory usage (stores each request timestamp)</li>
 * <li>More complex implementation</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rate-limit.storage-type", havingValue = "redis-lua")
public class RedisLuaRateLimiter implements RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;

    private static final String KEY_PREFIX = "ratelimit:lua:";

    /**
     * Lua script for sliding window rate limiting.
     * 
     * KEYS[1] = rate limit key
     * ARGV[1] = current timestamp (milliseconds)
     * ARGV[2] = window size (milliseconds)
     * ARGV[3] = max requests allowed in window
     * ARGV[4] = unique request ID (for ZADD)
     * 
     * Returns: [allowed (0/1), current_count, oldest_timestamp]
     */
    private static final String SLIDING_WINDOW_SCRIPT = """
            -- Remove expired entries
            local window_start = tonumber(ARGV[1]) - tonumber(ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', window_start)

            -- Count current entries in window
            local current_count = redis.call('ZCARD', KEYS[1])
            local limit = tonumber(ARGV[3])

            if current_count < limit then
                -- Add new entry with current timestamp as score
                redis.call('ZADD', KEYS[1], ARGV[1], ARGV[4])
                -- Set expiry on the key (window_size + buffer)
                redis.call('PEXPIRE', KEYS[1], tonumber(ARGV[2]) + 1000)
                return {1, current_count + 1, 0}
            else
                -- Rate limit exceeded, get time of oldest entry
                local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                local wait_time = 0
                if oldest and #oldest >= 2 then
                    wait_time = tonumber(oldest[2]) + tonumber(ARGV[2]) - tonumber(ARGV[1])
                end
                return {0, current_count, wait_time}
            end
            """;

    private RedisScript<List> slidingWindowScript;

    @PostConstruct
    public void init() {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(SLIDING_WINDOW_SCRIPT);
        script.setResultType(List.class);
        this.slidingWindowScript = script;
        log.info("Redis Lua rate limiter initialized with sliding window algorithm");
    }

    @Override
    public RateLimitResult tryConsume(String key, String tierName) {
        if (!rateLimitProperties.isEnabled()) {
            return RateLimitResult.allowed(createUnlimitedInfo());
        }

        RateLimitProperties.TierConfig tierConfig = rateLimitProperties.getTiers()
                .getOrDefault(tierName, getDefaultTierConfig());

        int limit = tierConfig.getRequestsPerMinute();
        long windowMs = 60_000; // 1 minute in milliseconds
        long now = System.currentTimeMillis();
        String requestId = now + ":" + Thread.currentThread().getId() + ":" + Math.random();

        String redisKey = KEY_PREFIX + key;

        try {
            @SuppressWarnings("unchecked")
            List<Long> result = redisTemplate.execute(
                    slidingWindowScript,
                    Collections.singletonList(redisKey),
                    String.valueOf(now),
                    String.valueOf(windowMs),
                    String.valueOf(limit),
                    requestId);

            if (result == null || result.size() < 3) {
                log.error("Lua script returned unexpected result for key: {}", redisKey);
                return RateLimitResult.allowed(createUnlimitedInfo()); // Fail open
            }

            boolean allowed = result.get(0) == 1;
            long currentCount = result.get(1);
            long waitTimeMs = result.get(2);

            RateLimitInfo info = RateLimitInfo.builder()
                    .limit(limit)
                    .remaining(Math.max(0, limit - currentCount))
                    .resetAt(Instant.now().getEpochSecond() + (allowed ? 60 : (waitTimeMs / 1000)))
                    .retryAfterSeconds(allowed ? 0 : Math.max(1, waitTimeMs / 1000))
                    .tier(tierName)
                    .build();

            if (allowed) {
                log.debug("Redis Lua: Request allowed for key: {}, count: {}/{}", key, currentCount, limit);
                return RateLimitResult.allowed(info);
            } else {
                log.info("Redis Lua: Rate limit exceeded for key: {}, count: {}/{}, retry in {}ms",
                        key, currentCount, limit, waitTimeMs);
                return RateLimitResult.denied(info);
            }
        } catch (Exception e) {
            log.error("Error executing Lua rate limit script for key: {}", key, e);
            return RateLimitResult.allowed(createUnlimitedInfo()); // Fail open
        }
    }

    @Override
    public RateLimitInfo getStatus(String key, String tierName) {
        RateLimitProperties.TierConfig tierConfig = rateLimitProperties.getTiers()
                .getOrDefault(tierName, getDefaultTierConfig());

        String redisKey = KEY_PREFIX + key;

        // Count current entries in window
        long windowStart = System.currentTimeMillis() - 60_000;
        Long count = redisTemplate.opsForZSet().count(redisKey, windowStart, Double.MAX_VALUE);
        long currentCount = count != null ? count : 0;

        return RateLimitInfo.builder()
                .limit(tierConfig.getRequestsPerMinute())
                .remaining(Math.max(0, tierConfig.getRequestsPerMinute() - currentCount))
                .resetAt(Instant.now().getEpochSecond() + 60)
                .retryAfterSeconds(currentCount >= tierConfig.getRequestsPerMinute() ? 60 : 0)
                .tier(tierName)
                .build();
    }

    @Override
    public void resetLimit(String key) {
        String redisKey = KEY_PREFIX + key;
        redisTemplate.delete(redisKey);
        log.info("Redis Lua: Reset rate limit for key: {}", key);
    }

    @Override
    public String getAlgorithmName() {
        return "Redis Lua (Sliding Window Log)";
    }

    private RateLimitProperties.TierConfig getDefaultTierConfig() {
        RateLimitProperties.TierConfig config = new RateLimitProperties.TierConfig();
        config.setRequestsPerMinute(rateLimitProperties.getDefaultLimit());
        config.setBurstCapacity(rateLimitProperties.getDefaultLimit());
        return config;
    }

    private RateLimitInfo createUnlimitedInfo() {
        return RateLimitInfo.builder()
                .limit(-1)
                .remaining(-1)
                .resetAt(0)
                .retryAfterSeconds(0)
                .tier("unlimited")
                .build();
    }
}
