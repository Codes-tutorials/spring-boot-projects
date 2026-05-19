package org.codeart.cache.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.cache.model.Article;
import org.codeart.cache.service.TrendingNewsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * News Website Controller - LFU Cache Demo
 */
@RestController
@RequestMapping("/api/news")
@RequiredArgsConstructor
@Tag(name = "News (LFU)", description = "Trending articles stay cached, one-time reads evicted")
public class NewsController {

    private final TrendingNewsService newsService;

    @GetMapping("/article/{id}")
    @Operation(summary = "Get article", description = "Frequently accessed articles stay cached (LFU)")
    public ResponseEntity<Article> getArticle(@PathVariable String id) {
        return newsService.getArticle(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/trending")
    @Operation(summary = "Get trending articles by engagement score")
    public ResponseEntity<List<Article>> getTrending(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(newsService.getTrendingArticles(limit));
    }

    @GetMapping("/frequency")
    @Operation(summary = "Get articles by access frequency (LFU demo)")
    public ResponseEntity<List<Map<String, Object>>> getByFrequency(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(newsService.getArticlesByFrequency(limit));
    }

    @PostMapping("/article")
    @Operation(summary = "Publish breaking news")
    public ResponseEntity<Article> publish(@RequestBody ArticleRequest request) {
        return ResponseEntity.ok(newsService.publishArticle(
                request.title(), request.content(), request.category()));
    }

    @PostMapping("/article/{id}/engagement")
    @Operation(summary = "Record engagement (makes article trend)")
    public ResponseEntity<Article> recordEngagement(
            @PathVariable String id,
            @RequestBody EngagementRequest request) {
        return ResponseEntity.ok(newsService.recordEngagement(
                id, request.views(), request.shares(), request.comments()));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get LFU cache statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(newsService.getCacheStats());
    }

    @GetMapping("/demo")
    @Operation(summary = "Demo LFU behavior", description = "Access same article multiple times to see frequency tracking")
    public ResponseEntity<Map<String, Object>> demo() {
        // Access trending article multiple times
        for (int i = 0; i < 5; i++) {
            newsService.getArticle("article-1");
        }
        // Access non-trending once
        newsService.getArticle("article-8");

        return ResponseEntity.ok(Map.of(
                "message", "Accessed article-1 5x times (high frequency), article-8 1x (low frequency)",
                "explanation", "LFU will keep article-1 cached and evict article-8 first when cache is full",
                "frequencyRanking", newsService.getArticlesByFrequency(5)));
    }

    public record ArticleRequest(String title, String content, String category) {
    }

    public record EngagementRequest(int views, int shares, int comments) {
    }
}
