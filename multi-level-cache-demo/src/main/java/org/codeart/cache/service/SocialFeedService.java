package org.codeart.cache.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.SocialPost;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Social Media Feed Cache Service
 * 
 * Implements LRU with interaction-based priority:
 * - Recent interactions stay cached
 * - Old posts nobody reads are evicted first
 * 
 * Real-world considerations:
 * - Users scroll through feeds frequently
 * - Posts they interact with should load fast
 * - Old/uninteracted posts can be evicted
 */
@Slf4j
@Service
public class SocialFeedService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    // LRU Cache with access-time based eviction
    private Cache<String, SocialPost> postCache;

    // User feed cache (list of post IDs per user)
    private Cache<String, List<String>> userFeedCache;

    // Simulated post database
    private final Map<String, SocialPost> postDatabase = new ConcurrentHashMap<>();

    // User follows (for generating feeds)
    private final Map<String, List<String>> userFollows = new ConcurrentHashMap<>();

    private static final String REDIS_PREFIX = "social:post:";
    private static final String FEED_PREFIX = "social:feed:";
    private static final int POST_CACHE_SIZE = 5000;
    private static final int FEED_CACHE_SIZE = 1000;
    private static final int TTL_MINUTES = 30;

    public SocialFeedService(RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        // LRU Cache: Evicts based on last access time
        this.postCache = Caffeine.newBuilder()
                .maximumSize(POST_CACHE_SIZE)
                .expireAfterAccess(TTL_MINUTES, TimeUnit.MINUTES) // KEY: expireAfterACCESS
                .recordStats()
                .build();

        // User feed cache
        this.userFeedCache = Caffeine.newBuilder()
                .maximumSize(FEED_CACHE_SIZE)
                .expireAfterWrite(5, TimeUnit.MINUTES) // Feeds refresh every 5 min
                .build();

        seedData();
        log.info("Social Feed cache initialized: LRU(posts={}, feeds={}), TTL={}min",
                POST_CACHE_SIZE, FEED_CACHE_SIZE, TTL_MINUTES);
    }

    /**
     * Get user's feed - cached with recency priority.
     */
    public List<SocialPost> getFeed(String userId, int limit) {
        // Check feed cache
        List<String> feedIds = userFeedCache.getIfPresent(userId);

        if (feedIds != null) {
            log.debug("FEED CACHE HIT for user: {}", userId);
            meterRegistry.counter("social.feed.cache.hit").increment();
        } else {
            log.info("FEED CACHE MISS for user: {}, building feed", userId);
            meterRegistry.counter("social.feed.cache.miss").increment();

            // Build feed from followed users
            feedIds = buildFeed(userId, limit);
            userFeedCache.put(userId, feedIds);
        }

        // Get posts for feed (posts may be individually cached)
        return feedIds.stream()
                .limit(limit)
                .map(this::getPost)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }

    /**
     * Get individual post with LRU caching.
     */
    public Optional<SocialPost> getPost(String postId) {
        // Check L1 cache
        SocialPost cached = postCache.getIfPresent(postId);
        if (cached != null) {
            cached.setLastInteractionAt(Instant.now());
            meterRegistry.counter("social.post.cache.hit").increment();
            return Optional.of(cached);
        }

        // Load from database
        meterRegistry.counter("social.post.cache.miss").increment();
        SocialPost post = postDatabase.get(postId);
        if (post != null) {
            post.setLastInteractionAt(Instant.now());
            postCache.put(postId, post);
        }

        return Optional.ofNullable(post);
    }

    /**
     * Like a post - keeps it in cache longer.
     */
    public SocialPost likePost(String postId, String userId) {
        SocialPost post = postDatabase.get(postId);
        if (post != null) {
            post.setLikes(post.getLikes() + 1);
            post.setLastInteractionAt(Instant.now());
            post.setInteractedWith(true);

            // Refresh in cache (extends TTL due to expireAfterAccess)
            postCache.put(postId, post);

            log.info("User {} liked post {} (now {} likes)", userId, postId, post.getLikes());
            meterRegistry.counter("social.post.like").increment();
        }
        return post;
    }

    /**
     * Create new post.
     */
    public SocialPost createPost(String userId, String content, SocialPost.PostType type) {
        String postId = "post-" + System.currentTimeMillis();
        SocialPost post = SocialPost.builder()
                .id(postId)
                .userId(userId)
                .username("user_" + userId)
                .content(content)
                .type(type)
                .likes(0)
                .comments(0)
                .shares(0)
                .createdAt(Instant.now())
                .lastInteractionAt(Instant.now())
                .build();

        postDatabase.put(postId, post);
        postCache.put(postId, post);

        // Invalidate followers' feed cache
        userFollows.entrySet().stream()
                .filter(e -> e.getValue().contains(userId))
                .forEach(e -> userFeedCache.invalidate(e.getKey()));

        log.info("User {} created post: {}", userId, postId);
        return post;
    }

    /**
     * Get stale posts (not interacted recently) - candidates for eviction.
     */
    public List<SocialPost> getStalePosts(int limit) {
        return postDatabase.values().stream()
                .filter(SocialPost::isStale)
                .sorted(Comparator.comparing(SocialPost::getLastInteractionAt))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        var postStats = postCache.stats();
        return Map.of(
                "strategy", "LRU (Least Recently Used) + Access Time",
                "description", "Recent interactions cached, old posts evicted",
                "postCacheSize", postCache.estimatedSize(),
                "feedCacheSize", userFeedCache.estimatedSize(),
                "hitCount", postStats.hitCount(),
                "missCount", postStats.missCount(),
                "hitRate", String.format("%.2f%%", postStats.hitRate() * 100),
                "evictionCount", postStats.evictionCount(),
                "ttlMinutes", TTL_MINUTES);
    }

    /**
     * Build feed for user from followed users.
     */
    private List<String> buildFeed(String userId, int limit) {
        List<String> following = userFollows.getOrDefault(userId, List.of());

        return postDatabase.values().stream()
                .filter(p -> following.contains(p.getUserId()) || p.getUserId().equals(userId))
                .sorted(Comparator.comparing(SocialPost::getCreatedAt).reversed())
                .limit(limit)
                .map(SocialPost::getId)
                .collect(Collectors.toList());
    }

    /**
     * Seed sample data.
     */
    private void seedData() {
        Random random = new Random();

        // Create users and follow relationships
        for (int i = 1; i <= 10; i++) {
            String userId = "user-" + i;
            List<String> follows = new ArrayList<>();
            for (int j = 1; j <= 10; j++) {
                if (i != j && random.nextBoolean()) {
                    follows.add("user-" + j);
                }
            }
            userFollows.put(userId, follows);
        }

        // Create posts
        SocialPost.PostType[] types = SocialPost.PostType.values();
        for (int i = 0; i < 50; i++) {
            String userId = "user-" + (random.nextInt(10) + 1);
            String postId = "post-" + (i + 1);

            SocialPost post = SocialPost.builder()
                    .id(postId)
                    .userId(userId)
                    .username("user_" + userId)
                    .content("Sample post content #" + (i + 1))
                    .type(types[random.nextInt(types.length)])
                    .likes(random.nextInt(1000))
                    .comments(random.nextInt(100))
                    .shares(random.nextInt(50))
                    .createdAt(Instant.now().minusSeconds(random.nextInt(86400 * 3)))
                    .lastInteractionAt(Instant.now().minusSeconds(random.nextInt(3600 * 12)))
                    .hashtags(List.of("#trending", "#social"))
                    .build();

            postDatabase.put(postId, post);

            // Pre-cache recent posts
            if (i < 20) {
                postCache.put(postId, post);
            }
        }

        log.info("Seeded {} users and {} posts", userFollows.size(), postDatabase.size());
    }
}
