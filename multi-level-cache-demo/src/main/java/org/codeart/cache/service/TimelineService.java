package org.codeart.cache.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.Timeline;
import org.codeart.cache.model.Tweet;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Twitter/X Timeline Cache Service
 * 
 * Implements LRU + TTL (30 seconds) caching strategy:
 * - LRU: Keep most recently accessed timelines, evict least used
 * - TTL: Each timeline expires after 30 seconds for freshness
 * 
 * Real-world considerations:
 * - Timelines change frequently (new tweets, likes, retweets)
 * - Popular users accessed frequently should stay in cache
 * - Stale data is acceptable for 30 seconds
 */
@Slf4j
@Service
public class TimelineService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;
    private final Timer cacheHitTimer;
    private final Timer cacheMissTimer;

    // L1 Cache: Caffeine with LRU eviction + TTL
    private Cache<String, Timeline> timelineCache;

    private static final String REDIS_PREFIX = "timeline:";
    private static final int TTL_SECONDS = 30;
    private static final int MAX_CACHE_SIZE = 10000; // LRU: keep 10K most recent

    public TimelineService(RedisTemplate<String, Object> redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
        this.cacheHitTimer = Timer.builder("timeline.cache.hit")
                .description("Timeline cache hit latency")
                .register(meterRegistry);
        this.cacheMissTimer = Timer.builder("timeline.cache.miss")
                .description("Timeline cache miss latency")
                .register(meterRegistry);
    }

    @PostConstruct
    public void init() {
        // Initialize Caffeine cache with LRU + TTL
        this.timelineCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_SIZE) // LRU: evict when full
                .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS) // TTL: 30 sec
                .recordStats() // For metrics
                .build();

        log.info("Timeline cache initialized: LRU(max={}), TTL={}s", MAX_CACHE_SIZE, TTL_SECONDS);
    }

    /**
     * Get user timeline with LRU + TTL caching.
     */
    public Timeline getTimeline(String userId) {
        return getTimeline(userId, 20); // Default 20 tweets
    }

    /**
     * Get user timeline with specified limit.
     */
    public Timeline getTimeline(String userId, int limit) {
        String cacheKey = userId + ":" + limit;

        // Check L1 (Caffeine - local, fast)
        Timeline cached = timelineCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("TIMELINE L1 HIT for user: {}", userId);
            cacheHitTimer.record(() -> {
            });
            meterRegistry.counter("timeline.cache.l1.hit").increment();
            return cached;
        }

        // Check L2 (Redis - distributed)
        try {
            Object redisValue = redisTemplate.opsForValue().get(REDIS_PREFIX + cacheKey);
            if (redisValue instanceof Timeline) {
                cached = (Timeline) redisValue;
                log.debug("TIMELINE L2 HIT for user: {}, promoting to L1", userId);
                meterRegistry.counter("timeline.cache.l2.hit").increment();
                // Promote to L1
                timelineCache.put(cacheKey, cached);
                return cached;
            }
        } catch (Exception e) {
            log.warn("Redis read failed for timeline:{}, falling back to DB", userId, e);
        }

        // Cache miss - load from "database" (simulated)
        log.info("TIMELINE MISS for user: {}, loading from database", userId);
        meterRegistry.counter("timeline.cache.miss").increment();

        Timeline timeline = cacheMissTimer.record(() -> loadTimelineFromDatabase(userId, limit));

        // Cache in both L1 and L2
        timelineCache.put(cacheKey, timeline);
        try {
            redisTemplate.opsForValue().set(
                    REDIS_PREFIX + cacheKey,
                    timeline,
                    Duration.ofSeconds(TTL_SECONDS));
        } catch (Exception e) {
            log.warn("Redis write failed for timeline:{}", userId, e);
        }

        return timeline;
    }

    /**
     * Invalidate timeline cache when user posts new tweet.
     */
    public void invalidateTimeline(String userId) {
        log.info("Invalidating timeline cache for user: {}", userId);

        // Invalidate all variants (different limits)
        for (int limit : List.of(10, 20, 50, 100)) {
            String cacheKey = userId + ":" + limit;
            timelineCache.invalidate(cacheKey);
            redisTemplate.delete(REDIS_PREFIX + cacheKey);
        }

        meterRegistry.counter("timeline.cache.invalidation").increment();
    }

    /**
     * Add tweet to timeline and invalidate cache.
     */
    public Tweet postTweet(String userId, String content) {
        Tweet tweet = Tweet.builder()
                .id(UUID.randomUUID().toString())
                .userId(userId)
                .username("user_" + userId)
                .content(content)
                .likes(0)
                .retweets(0)
                .replies(0)
                .createdAt(Instant.now())
                .build();

        // In real app: save to database
        log.info("User {} posted tweet: {}", userId, tweet.getId());

        // Invalidate cache so next read gets fresh data
        invalidateTimeline(userId);

        return tweet;
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        var stats = timelineCache.stats();
        return Map.of(
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "hitRate", String.format("%.2f%%", stats.hitRate() * 100),
                "evictionCount", stats.evictionCount(),
                "estimatedSize", timelineCache.estimatedSize(),
                "maxSize", MAX_CACHE_SIZE,
                "ttlSeconds", TTL_SECONDS);
    }

    /**
     * Simulate database load (expensive operation).
     */
    private Timeline loadTimelineFromDatabase(String userId, int limit) {
        // Simulate database latency (100-300ms)
        try {
            Thread.sleep(100 + new Random().nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Generate sample tweets
        List<Tweet> tweets = new ArrayList<>();
        Instant now = Instant.now();

        for (int i = 0; i < limit; i++) {
            tweets.add(Tweet.builder()
                    .id(UUID.randomUUID().toString())
                    .userId(userId)
                    .username("user_" + userId)
                    .content("Tweet #" + (i + 1) + " from user " + userId + " at " + now)
                    .likes((int) (Math.random() * 1000))
                    .retweets((int) (Math.random() * 100))
                    .replies((int) (Math.random() * 50))
                    .createdAt(now.minusSeconds(i * 60L))
                    .build());
        }

        return Timeline.builder()
                .userId(userId)
                .tweets(tweets)
                .cachedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(TTL_SECONDS))
                .totalTweets(tweets.size())
                .cursorNext("cursor_" + System.currentTimeMillis())
                .build();
    }
}
