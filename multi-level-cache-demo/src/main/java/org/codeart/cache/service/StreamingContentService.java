package org.codeart.cache.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.VideoContent;
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
 * Netflix/YouTube Streaming Content Cache Service
 * 
 * Implements activity-based caching:
 * - Recently watched content stays cached
 * - Inactive content (not watched in 7 days) gets evicted
 * - Currently watching (in-progress) content has highest priority
 * 
 * Real-world considerations:
 * - Users often rewatch or continue watching content
 * - Popular content accessed by many users
 * - Content metadata (not actual video) is cached
 */
@Slf4j
@Service
public class StreamingContentService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    // Content metadata cache (LRU by access time)
    private Cache<String, VideoContent> contentCache;

    // User watch history cache (per user)
    private Cache<String, List<String>> watchHistoryCache;

    // Currently watching cache (high priority, never evicted while active)
    private Cache<String, VideoContent> currentlyWatchingCache;

    // Simulated content database
    private final Map<String, VideoContent> contentDatabase = new ConcurrentHashMap<>();

    // User watch history
    private final Map<String, List<WatchRecord>> userWatchHistory = new ConcurrentHashMap<>();

    private static final String REDIS_PREFIX = "streaming:content:";
    private static final int CONTENT_CACHE_SIZE = 2000;
    private static final int WATCHING_CACHE_SIZE = 500;
    private static final int TTL_HOURS_CONTENT = 24;
    private static final int TTL_MINUTES_WATCHING = 120; // 2 hours for active watching

    public StreamingContentService(RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        // Main content cache: LRU with access-time expiry
        this.contentCache = Caffeine.newBuilder()
                .maximumSize(CONTENT_CACHE_SIZE)
                .expireAfterAccess(TTL_HOURS_CONTENT, TimeUnit.HOURS)
                .recordStats()
                .build();

        // Currently watching: Higher priority, longer TTL
        this.currentlyWatchingCache = Caffeine.newBuilder()
                .maximumSize(WATCHING_CACHE_SIZE)
                .expireAfterWrite(TTL_MINUTES_WATCHING, TimeUnit.MINUTES)
                .build();

        // Watch history per user
        this.watchHistoryCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(1, TimeUnit.HOURS)
                .build();

        seedContent();
        log.info("Streaming cache initialized: content={}, watching={}",
                CONTENT_CACHE_SIZE, WATCHING_CACHE_SIZE);
    }

    /**
     * Get content details with caching.
     */
    public Optional<VideoContent> getContent(String contentId) {
        // Check currently watching cache first (highest priority)
        VideoContent watching = currentlyWatchingCache.getIfPresent(contentId);
        if (watching != null) {
            log.debug("STREAMING: Currently watching cache HIT: {}", watching.getTitle());
            meterRegistry.counter("streaming.cache.watching.hit").increment();
            return Optional.of(watching);
        }

        // Check main content cache
        VideoContent cached = contentCache.getIfPresent(contentId);
        if (cached != null) {
            cached.setLastWatchedAt(Instant.now());
            log.debug("STREAMING: Content cache HIT: {}", cached.getTitle());
            meterRegistry.counter("streaming.cache.content.hit").increment();
            return Optional.of(cached);
        }

        // Load from database
        log.info("STREAMING: Cache MISS for content: {}", contentId);
        meterRegistry.counter("streaming.cache.miss").increment();

        VideoContent content = contentDatabase.get(contentId);
        if (content != null) {
            content.setLastWatchedAt(Instant.now());
            contentCache.put(contentId, content);
        }

        return Optional.ofNullable(content);
    }

    /**
     * Start watching - moves content to high-priority cache.
     */
    public VideoContent startWatching(String userId, String contentId) {
        VideoContent content = contentDatabase.get(contentId);
        if (content == null) {
            return null;
        }

        content.setLastWatchedAt(Instant.now());
        content.setWatchProgress(0);

        // Move to currently watching cache (high priority)
        currentlyWatchingCache.put(contentId, content);

        // Record in watch history
        recordWatch(userId, contentId, 0);

        log.info("User {} started watching: {}", userId, content.getTitle());
        meterRegistry.counter("streaming.watch.start").increment();

        return content;
    }

    /**
     * Update watch progress.
     */
    public VideoContent updateProgress(String userId, String contentId, int progressPercent) {
        VideoContent content = contentDatabase.get(contentId);
        if (content == null) {
            return null;
        }

        content.setLastWatchedAt(Instant.now());
        content.setWatchProgress(progressPercent);

        // Keep in currently watching cache
        currentlyWatchingCache.put(contentId, content);

        // Update watch record
        recordWatch(userId, contentId, progressPercent);

        log.debug("User {} progress on {}: {}%", userId, content.getTitle(), progressPercent);

        if (progressPercent >= 95) {
            log.info("User {} completed watching: {}", userId, content.getTitle());
            meterRegistry.counter("streaming.watch.complete").increment();

            // Move from currently watching to regular cache
            currentlyWatchingCache.invalidate(contentId);
            contentCache.put(contentId, content);
        }

        return content;
    }

    /**
     * Get user's continue watching list.
     */
    public List<VideoContent> getContinueWatching(String userId) {
        List<WatchRecord> history = userWatchHistory.getOrDefault(userId, List.of());

        return history.stream()
                .filter(r -> r.progress > 0 && r.progress < 95)
                .sorted(Comparator.comparing(WatchRecord::getWatchedAt).reversed())
                .limit(10)
                .map(r -> contentDatabase.get(r.contentId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Get recommendations (based on watch history).
     */
    public List<VideoContent> getRecommendations(String userId, int limit) {
        List<WatchRecord> history = userWatchHistory.getOrDefault(userId, List.of());

        Set<String> watchedGenres = history.stream()
                .map(r -> contentDatabase.get(r.contentId))
                .filter(Objects::nonNull)
                .map(VideoContent::getGenre)
                .collect(Collectors.toSet());

        return contentDatabase.values().stream()
                .filter(c -> watchedGenres.contains(c.getGenre()))
                .filter(c -> history.stream().noneMatch(r -> r.contentId.equals(c.getId())))
                .sorted(Comparator.comparingDouble(VideoContent::getRating).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get inactive content (not watched in 7+ days) - eviction candidates.
     */
    public List<VideoContent> getInactiveContent(int limit) {
        return contentDatabase.values().stream()
                .filter(VideoContent::isInactive)
                .sorted(Comparator.comparing(VideoContent::getLastWatchedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get trending content.
     */
    public List<VideoContent> getTrending(int limit) {
        return contentDatabase.values().stream()
                .sorted(Comparator.comparingLong(VideoContent::getViewCount).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        var contentStats = contentCache.stats();
        return Map.of(
                "strategy", "Activity-based LRU",
                "description", "Recently watched cached, inactive evicted after 7 days",
                "contentCacheSize", contentCache.estimatedSize(),
                "currentlyWatchingSize", currentlyWatchingCache.estimatedSize(),
                "hitCount", contentStats.hitCount(),
                "missCount", contentStats.missCount(),
                "hitRate", String.format("%.2f%%", contentStats.hitRate() * 100),
                "evictionCount", contentStats.evictionCount(),
                "ttlHoursContent", TTL_HOURS_CONTENT,
                "ttlMinutesWatching", TTL_MINUTES_WATCHING);
    }

    /**
     * Record watch activity.
     */
    private void recordWatch(String userId, String contentId, int progress) {
        userWatchHistory.computeIfAbsent(userId, k -> new ArrayList<>());
        List<WatchRecord> history = userWatchHistory.get(userId);

        // Update existing or add new
        history.removeIf(r -> r.contentId.equals(contentId));
        history.add(0, new WatchRecord(contentId, progress, Instant.now()));

        // Keep only last 100 records
        if (history.size() > 100) {
            history.subList(100, history.size()).clear();
        }
    }

    /**
     * Seed sample content.
     */
    private void seedContent() {
        String[] titles = {
                "The Matrix", "Inception", "Interstellar", "The Dark Knight",
                "Stranger Things", "Breaking Bad", "Game of Thrones", "The Office",
                "Planet Earth", "Cosmos", "Our Planet",
                "The Avengers", "Black Panther", "Spider-Man"
        };
        String[] genres = { "Sci-Fi", "Action", "Drama", "Thriller", "Documentary", "Comedy" };
        VideoContent.ContentType[] types = VideoContent.ContentType.values();
        Random random = new Random();

        for (int i = 0; i < titles.length; i++) {
            String id = "content-" + (i + 1);
            VideoContent content = VideoContent.builder()
                    .id(id)
                    .title(titles[i])
                    .description("Description for " + titles[i])
                    .thumbnailUrl("/thumbnails/" + id + ".jpg")
                    .streamUrl("/stream/" + id)
                    .durationSeconds(random.nextInt(7200) + 1800)
                    .type(i < 4 ? VideoContent.ContentType.MOVIE
                            : i < 8 ? VideoContent.ContentType.TV_SERIES : VideoContent.ContentType.DOCUMENTARY)
                    .genre(genres[random.nextInt(genres.length)])
                    .rating(3.0 + random.nextDouble() * 2)
                    .viewCount(random.nextLong(10000000))
                    .releaseDate(Instant.now().minusSeconds(random.nextInt(86400 * 365 * 3)))
                    .lastWatchedAt(Instant.now().minusSeconds(random.nextInt(86400 * 30)))
                    .watchProgress(0)
                    .build();

            contentDatabase.put(id, content);

            // Pre-cache popular content
            if (content.getViewCount() > 5000000) {
                contentCache.put(id, content);
            }
        }

        log.info("Seeded {} streaming content items", contentDatabase.size());
    }

    /**
     * Watch history record.
     */
    private record WatchRecord(String contentId, int progress, Instant watchedAt) {
        public Instant getWatchedAt() {
            return watchedAt;
        }
    }
}
