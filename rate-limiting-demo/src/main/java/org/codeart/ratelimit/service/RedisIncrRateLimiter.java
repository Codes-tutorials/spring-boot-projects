package org.codeart.ratelimit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.config.RateLimitProperties;
import org.codeart.ratelimit.dto.RateLimitInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Redis-only rate limiter using INCR + EXPIRE commands.
 * Implements a Fixed Window Counter algorithm.
 * 
 * <p>
 * How it works:
 * </p>
 * <ol>
 * <li>INCR the counter for the key</li>
 * <li>If counter == 1, set EXPIRE for the window duration</li>
 * <li>If counter > limit, reject the request</li>
 * </ol>
 * 
 * <p>
 * Pros:
 * </p>
 * <ul>
 * <li>Simple implementation</li>
 * <li>Low Redis operations (2 ops max per request)</li>
 * <li>No external library needed</li>
 * </ul>
 * 
 * <p>
 * Cons:
 * </p>
 * <ul>
 * <li>Fixed window can allow 2x burst at window boundaries</li>
 * <li>Example: 100 requests at 0:59, then 100 more at 1:00</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rate-limit.storage-type", havingValue = "redis-incr")
public class RedisIncrRateLimiter implements RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties rateLimitProperties;

    private static final String KEY_PREFIX = "ratelimit:incr:";

    @Override
    public RateLimitResult tryConsume(String key, String tierName) {
        if (!rateLimitProperties.isEnabled()) {
            return RateLimitResult.allowed(createUnlimitedInfo());
        }

        RateLimitProperties.TierConfig tierConfig = rateLimitProperties.getTiers()
                .getOrDefault(tierName, getDefaultTierConfig());

        int limit = tierConfig.getRequestsPerMinute();
        long windowSeconds = 60; // 1 minute window

        String redisKey = KEY_PREFIX + key;

        // Increment the counter
        Long currentCount = redisTemplate.opsForValue().increment(redisKey);

        if (currentCount == null) {
            log.error("Redis INCR returned null for key: {}", redisKey);
            return RateLimitResult.allowed(createUnlimitedInfo()); // Fail open
        }

        // Set expiry on first request
        if (currentCount == 1) {
            redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
        }

        // Get TTL for reset time
        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
        long resetAt = Instant.now().getEpochSecond() + (ttl != null ? ttl : windowSeconds);

        RateLimitInfo info = RateLimitInfo.builder()
                .limit(limit)
                .remaining(Math.max(0, limit - currentCount))
                .resetAt(resetAt)
                .retryAfterSeconds(ttl != null ? ttl : windowSeconds)
                .tier(tierName)
                .build();

        if (currentCount <= limit) {
            log.debug("Redis INCR: Request allowed for key: {}, count: {}/{}", key, currentCount, limit);
            return RateLimitResult.allowed(info);
        } else {
            log.info("Redis INCR: Rate limit exceeded for key: {}, count: {}/{}", key, currentCount, limit);
            return RateLimitResult.denied(info);
        }
    }

    @Override
    public RateLimitInfo getStatus(String key, String tierName) {
        RateLimitProperties.TierConfig tierConfig = rateLimitProperties.getTiers()
                .getOrDefault(tierName, getDefaultTierConfig());

        String redisKey = KEY_PREFIX + key;
        String countStr = redisTemplate.opsForValue().get(redisKey);
        long currentCount = countStr != null ? Long.parseLong(countStr) : 0;

        Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);

        return RateLimitInfo.builder()
                .limit(tierConfig.getRequestsPerMinute())
                .remaining(Math.max(0, tierConfig.getRequestsPerMinute() - currentCount))
                .resetAt(Instant.now().getEpochSecond() + (ttl != null && ttl > 0 ? ttl : 60))
                .retryAfterSeconds(ttl != null && ttl > 0 ? ttl : 60)
                .tier(tierName)
                .build();
    }

    @Override
    public void resetLimit(String key) {
        String redisKey = KEY_PREFIX + key;
        redisTemplate.delete(redisKey);
        log.info("Redis INCR: Reset rate limit for key: {}", key);
    }

    @Override
    public String getAlgorithmName() {
        return "Redis INCR (Fixed Window)";
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
