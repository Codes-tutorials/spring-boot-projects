package org.codeart.cache.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.Article;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * News Website Trending Article Cache Service
 * 
 * Implements LFU (Least Frequently Used) eviction:
 * - Trending articles (high access frequency) stay cached
 * - One-time reads (low frequency) are evicted first
 * 
 * Real-world considerations:
 * - Trending articles get 100x more traffic than old articles
 * - Breaking news needs to be cached quickly
 * - Old/less popular articles should be evicted to save memory
 */
@Slf4j
@Service
public class TrendingNewsService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    // LFU Cache: Evict least frequently accessed articles
    private Cache<String, Article> articleCache;

    // Access frequency tracker (for demo visibility)
    private final Map<String, AtomicLong> accessFrequency = new ConcurrentHashMap<>();

    // Simulated article database
    private final Map<String, Article> articleDatabase = new ConcurrentHashMap<>();

    private static final String REDIS_PREFIX = "news:article:";
    private static final String TRENDING_KEY = "news:trending";
    private static final int CACHE_SIZE = 1000; // Only top 1000 articles cached
    private static final int TTL_MINUTES = 60;

    public TrendingNewsService(RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        // LFU Cache: Evicts LEAST FREQUENTLY used articles
        this.articleCache = Caffeine.newBuilder()
                .maximumSize(CACHE_SIZE)
                .expireAfterWrite(TTL_MINUTES, TimeUnit.MINUTES)
                .recordStats()
                .build();

        seedArticles();
        log.info("Trending News cache initialized: LFU(max={}), TTL={}min",
                CACHE_SIZE, TTL_MINUTES);
    }

    /**
     * Get article - Frequently accessed articles stay cached.
     */
    public Optional<Article> getArticle(String articleId) {
        // Track access frequency
        accessFrequency.computeIfAbsent(articleId, k -> new AtomicLong()).incrementAndGet();

        // Check L1 cache
        Article cached = articleCache.getIfPresent(articleId);
        if (cached != null) {
            cached.setAccessCount(cached.getAccessCount() + 1);
            cached.setLastAccessedAt(Instant.now());
            log.debug("NEWS CACHE HIT: {} (frequency={})",
                    cached.getTitle(), accessFrequency.get(articleId).get());
            meterRegistry.counter("news.cache.hit").increment();
            return Optional.of(cached);
        }

        // Cache miss - load from database
        log.info("NEWS CACHE MISS: articleId={}", articleId);
        meterRegistry.counter("news.cache.miss").increment();

        Article article = articleDatabase.get(articleId);
        if (article != null) {
            article.setAccessCount(article.getAccessCount() + 1);
            article.setLastAccessedAt(Instant.now());

            // Cache it (LFU will evict least accessed)
            articleCache.put(articleId, article);

            // Also update Redis
            try {
                redisTemplate.opsForValue().set(
                        REDIS_PREFIX + articleId,
                        article,
                        Duration.ofMinutes(TTL_MINUTES));
            } catch (Exception e) {
                log.warn("Redis write failed for article: {}", articleId);
            }
        }

        return Optional.ofNullable(article);
    }

    /**
     * Get trending articles (most accessed).
     */
    public List<Article> getTrendingArticles(int limit) {
        return articleDatabase.values().stream()
                .peek(a -> a.setTrendingScore(a.calculateTrendingScore()))
                .sorted(Comparator.comparingDouble(Article::getTrendingScore).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get articles by access frequency (for demo).
     */
    public List<Map<String, Object>> getArticlesByFrequency(int limit) {
        return accessFrequency.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().get(), e1.getValue().get()))
                .limit(limit)
                .map(e -> {
                    Article article = articleDatabase.get(e.getKey());
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", e.getKey());
                    result.put("title", article != null ? article.getTitle() : "Unknown");
                    result.put("frequency", e.getValue().get());
                    result.put("inCache", articleCache.getIfPresent(e.getKey()) != null);
                    return result;
                })
                .collect(Collectors.toList());
    }

    /**
     * Publish new article (breaking news).
     */
    public Article publishArticle(String title, String content, String category) {
        String id = "article-" + System.currentTimeMillis();
        Article article = Article.builder()
                .id(id)
                .title(title)
                .content(content)
                .category(category)
                .author("Editor")
                .views(0)
                .shares(0)
                .comments(0)
                .trending(true)
                .publishedAt(Instant.now())
                .lastAccessedAt(Instant.now())
                .accessCount(0)
                .build();

        articleDatabase.put(id, article);

        // Immediately cache breaking news
        articleCache.put(id, article);

        log.info("Breaking news published: {}", title);
        return article;
    }

    /**
     * Simulate article engagement (views, shares).
     */
    public Article recordEngagement(String articleId, int views, int shares, int comments) {
        Article article = articleDatabase.get(articleId);
        if (article != null) {
            article.setViews(article.getViews() + views);
            article.setShares(article.getShares() + shares);
            article.setComments(article.getComments() + comments);
            article.setTrendingScore(article.calculateTrendingScore());

            // High engagement = update cache
            if (shares > 10 || comments > 5) {
                articleCache.put(articleId, article);
                log.info("Article {} trending! Score: {}",
                        article.getTitle(), article.getTrendingScore());
            }
        }
        return article;
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        var stats = articleCache.stats();
        return Map.of(
                "strategy", "LFU (Least Frequently Used)",
                "description", "Trending articles stay cached, one-time reads evicted",
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "hitRate", String.format("%.2f%%", stats.hitRate() * 100),
                "evictionCount", stats.evictionCount(),
                "cacheSize", articleCache.estimatedSize(),
                "maxSize", CACHE_SIZE,
                "ttlMinutes", TTL_MINUTES);
    }

    /**
     * Seed sample articles.
     */
    private void seedArticles() {
        String[] categories = { "TECH", "SPORTS", "POLITICS", "ENTERTAINMENT", "SCIENCE" };
        String[] titles = {
                "Breaking: Major Tech Company Announces AI Breakthrough",
                "Sports: Championship Finals Tonight",
                "Politics: New Policy Changes Announced",
                "Entertainment: Blockbuster Movie Sets Records",
                "Science: Mars Rover Discovers Water Evidence",
                "Tech: New Smartphone Released",
                "Sports: Transfer News Updates",
                "Old Article: Archive Story from Last Month",
                "Trending: Viral Video Breaks Internet",
                "Local: Community Event This Weekend"
        };

        Random random = new Random();
        for (int i = 0; i < titles.length; i++) {
            String id = "article-" + (i + 1);
            String category = categories[i % categories.length];

            Article article = Article.builder()
                    .id(id)
                    .title(titles[i])
                    .content("Content for: " + titles[i])
                    .category(category)
                    .author("Author " + (i + 1))
                    .views(random.nextInt(10000))
                    .shares(random.nextInt(500))
                    .comments(random.nextInt(200))
                    .trending(i < 5) // First 5 are trending
                    .publishedAt(Instant.now().minusSeconds(random.nextInt(86400 * 7)))
                    .lastAccessedAt(Instant.now())
                    .accessCount(0)
                    .tags(List.of(category.toLowerCase(), "news"))
                    .build();

            article.setTrendingScore(article.calculateTrendingScore());
            articleDatabase.put(id, article);

            // Pre-cache trending articles
            if (article.isTrending()) {
                articleCache.put(id, article);
            }
        }

        log.info("Seeded {} articles ({} trending)", articleDatabase.size(), 5);
    }
}
