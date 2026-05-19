package org.codeart.cache.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.Timeline;
import org.codeart.cache.model.Tweet;
import org.codeart.cache.service.TimelineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Twitter/X Timeline Controller
 * Demonstrates LRU + TTL (30 sec) caching for social media feeds.
 */
@Slf4j
@RestController
@RequestMapping("/api/timeline")
@RequiredArgsConstructor
@Tag(name = "Timeline (Twitter/X)", description = "Social media timeline with LRU + TTL caching")
public class TimelineController {

    private final TimelineService timelineService;

    @GetMapping("/{userId}")
    @Operation(summary = "Get user timeline", description = "Fetches user's timeline with LRU + TTL (30s) caching. " +
            "First call is slow (~200ms), subsequent calls are fast until TTL expires.")
    public ResponseEntity<Timeline> getTimeline(
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit) {

        long start = System.currentTimeMillis();
        Timeline timeline = timelineService.getTimeline(userId, limit);
        long elapsed = System.currentTimeMillis() - start;

        log.info("Timeline for user {} returned in {}ms", userId, elapsed);

        return ResponseEntity.ok(timeline);
    }

    @PostMapping("/{userId}/tweet")
    @Operation(summary = "Post a tweet", description = "Posts a tweet and invalidates the user's timeline cache")
    public ResponseEntity<Tweet> postTweet(
            @PathVariable String userId,
            @RequestBody TweetRequest request) {

        Tweet tweet = timelineService.postTweet(userId, request.content());
        return ResponseEntity.ok(tweet);
    }

    @DeleteMapping("/{userId}/cache")
    @Operation(summary = "Invalidate user timeline cache", description = "Manually invalidate cache (e.g., when user changes settings)")
    public ResponseEntity<Map<String, String>> invalidateCache(@PathVariable String userId) {
        timelineService.invalidateTimeline(userId);
        return ResponseEntity.ok(Map.of(
                "status", "invalidated",
                "userId", userId));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get cache statistics")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(timelineService.getCacheStats());
    }

    /**
     * Demo endpoint to show cache behavior.
     */
    @GetMapping("/demo")
    @Operation(summary = "Demo cache behavior", description = "Call this twice rapidly to see cache HIT on second call")
    public ResponseEntity<Map<String, Object>> demo() {
        String userId = "demo-user";

        long start = System.currentTimeMillis();
        Timeline timeline = timelineService.getTimeline(userId);
        long elapsed = System.currentTimeMillis() - start;

        return ResponseEntity.ok(Map.of(
                "message", elapsed < 50 ? "CACHE HIT - Fast!" : "CACHE MISS - Loaded from DB",
                "latencyMs", elapsed,
                "ttlSeconds", 30,
                "strategy", "LRU + TTL",
                "tweets", timeline.getTweets().size()));
    }

    public record TweetRequest(String content) {
    }
}
