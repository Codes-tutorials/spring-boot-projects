package org.codeart.ratelimit.service;

import org.codeart.ratelimit.dto.RateLimitInfo;

/**
 * Interface for Redis-based rate limiters.
 * Allows switching between different Redis rate limiting strategies.
 */
public interface RedisRateLimiter {

    /**
     * Try to consume a token for the given key.
     * 
     * @param key      the rate limit key
     * @param tierName the tier name for configuration
     * @return result containing allowed status and rate limit info
     */
    RateLimitResult tryConsume(String key, String tierName);

    /**
     * Get current rate limit status without consuming.
     */
    RateLimitInfo getStatus(String key, String tierName);

    /**
     * Reset rate limit for a specific key.
     */
    void resetLimit(String key);

    /**
     * Get the algorithm name for logging/metrics.
     */
    String getAlgorithmName();

    /**
     * Result of a rate limit check.
     */
    record RateLimitResult(boolean allowed, RateLimitInfo info) {
        public static RateLimitResult allowed(RateLimitInfo info) {
            return new RateLimitResult(true, info);
        }

        public static RateLimitResult denied(RateLimitInfo info) {
            return new RateLimitResult(false, info);
        }
    }
}
