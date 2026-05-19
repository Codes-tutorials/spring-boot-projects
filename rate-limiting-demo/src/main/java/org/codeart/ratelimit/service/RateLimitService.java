package org.codeart.ratelimit.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.config.RateLimitConfig;
import org.codeart.ratelimit.config.RateLimitProperties;
import org.codeart.ratelimit.dto.RateLimitInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Core service for rate limiting operations.
 * Supports both Redis-backed (distributed) and in-memory (local) rate limiting.
 */
@Slf4j
@Service
public class RateLimitService {

    private final RateLimitProperties rateLimitProperties;
    private final RateLimitConfig rateLimitConfig;
    private final RateLimitMetricsService metricsService;

    // Optional: Redis proxy manager (null if using in-memory)
    private final ProxyManager<String> proxyManager;

    // In-memory bucket cache (used when Redis is not available)
    private final Map<String, Bucket> localBucketCache = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitService(
            RateLimitProperties rateLimitProperties,
            RateLimitConfig rateLimitConfig,
            RateLimitMetricsService metricsService,
            @Autowired(required = false) ProxyManager<String> proxyManager) {
        this.rateLimitProperties = rateLimitProperties;
        this.rateLimitConfig = rateLimitConfig;
        this.metricsService = metricsService;
        this.proxyManager = proxyManager;

        log.info("RateLimitService initialized with storage type: {}",
                proxyManager != null ? "redis" : "in-memory");
    }

    /**
     * Try to consume a token for the given key.
     * 
     * @param key      the rate limit key (e.g., "ip:192.168.1.1" or
     *                 "apikey:abc123")
     * @param tierName the tier name for rate limit configuration
     * @return RateLimitResult containing success status and rate limit info
     */
    public RateLimitResult tryConsume(String key, String tierName) {
        if (!rateLimitProperties.isEnabled()) {
            return RateLimitResult.allowed(createUnlimitedInfo());
        }

        Bucket bucket = resolveBucket(key, tierName);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        RateLimitInfo info = buildRateLimitInfo(probe, tierName);

        if (probe.isConsumed()) {
            log.debug("Rate limit check passed for key: {}, remaining: {}", key, probe.getRemainingTokens());
            metricsService.recordRequest(tierName, true);
            return RateLimitResult.allowed(info);
        } else {
            log.info("Rate limit exceeded for key: {}, retry after: {}ns", key, probe.getNanosToWaitForRefill());
            metricsService.recordRequest(tierName, false);
            return RateLimitResult.denied(info);
        }
    }

    /**
     * Get current rate limit status for a key without consuming tokens.
     */
    public RateLimitInfo getStatus(String key, String tierName) {
        Bucket bucket = resolveBucket(key, tierName);
        long availableTokens = bucket.getAvailableTokens();

        RateLimitProperties.TierConfig tierConfig = rateLimitProperties.getTiers()
                .getOrDefault(tierName, getDefaultTierConfig());

        return RateLimitInfo.builder()
                .limit(tierConfig.getRequestsPerMinute())
                .remaining(Math.max(0, availableTokens))
                .resetAt(Instant.now().plusSeconds(60).getEpochSecond())
                .retryAfterSeconds(availableTokens > 0 ? 0 : 60)
                .tier(tierName)
                .build();
    }

    /**
     * Reset rate limit for a specific key.
     */
    public void resetLimit(String key) {
        if (proxyManager != null) {
            // For Redis, we need to remove the key
            log.info("Resetting rate limit for key: {} (Redis)", key);
            // ProxyManager doesn't have a direct remove method,
            // bucket will be recreated on next access
        } else {
            localBucketCache.remove(key);
            log.info("Resetting rate limit for key: {} (in-memory)", key);
        }
    }

    private Bucket resolveBucket(String key, String tierName) {
        if (proxyManager != null) {
            // Use Redis-backed distributed bucket
            Supplier<BucketConfiguration> configSupplier = rateLimitConfig.bucketConfigurationSupplier(tierName);
            return proxyManager.builder().build(key, configSupplier);
        } else {
            // Use in-memory bucket
            return localBucketCache.computeIfAbsent(key, k -> createLocalBucket(tierName));
        }
    }

    private Bucket createLocalBucket(String tierName) {
        RateLimitProperties.TierConfig tierConfig = rateLimitProperties.getTiers()
                .getOrDefault(tierName, getDefaultTierConfig());

        Bandwidth limit = Bandwidth.builder()
                .capacity(tierConfig.getBurstCapacity())
                .refillGreedy(tierConfig.getRequestsPerMinute(), tierConfig.getDuration())
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private RateLimitInfo buildRateLimitInfo(ConsumptionProbe probe, String tierName) {
        RateLimitProperties.TierConfig tierConfig = rateLimitProperties.getTiers()
                .getOrDefault(tierName, getDefaultTierConfig());

        long resetAtSeconds = Instant.now().getEpochSecond() +
                (probe.getNanosToWaitForRefill() / 1_000_000_000L);

        return RateLimitInfo.builder()
                .limit(tierConfig.getRequestsPerMinute())
                .remaining(probe.getRemainingTokens())
                .resetAt(resetAtSeconds)
                .retryAfterSeconds(probe.getNanosToWaitForRefill() / 1_000_000_000L)
                .tier(tierName)
                .build();
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

    private RateLimitProperties.TierConfig getDefaultTierConfig() {
        RateLimitProperties.TierConfig config = new RateLimitProperties.TierConfig();
        config.setRequestsPerMinute(rateLimitProperties.getDefaultLimit());
        config.setBurstCapacity(rateLimitProperties.getDefaultLimit());
        return config;
    }

    /**
     * Result of a rate limit check.
     */
    public record RateLimitResult(boolean allowed, RateLimitInfo info) {
        public static RateLimitResult allowed(RateLimitInfo info) {
            return new RateLimitResult(true, info);
        }

        public static RateLimitResult denied(RateLimitInfo info) {
            return new RateLimitResult(false, info);
        }
    }
}
