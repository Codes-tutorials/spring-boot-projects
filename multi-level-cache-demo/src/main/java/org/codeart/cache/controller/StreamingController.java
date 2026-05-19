package org.codeart.cache.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.cache.model.VideoContent;
import org.codeart.cache.service.StreamingContentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Netflix/YouTube Streaming Controller - Activity-based Cache
 */
@RestController
@RequestMapping("/api/streaming")
@RequiredArgsConstructor
@Tag(name = "Streaming (Activity)", description = "Recently watched cached, inactive evicted")
public class StreamingController {

    private final StreamingContentService streamingService;

    @GetMapping("/content/{id}")
    @Operation(summary = "Get content metadata", description = "Recently accessed content stays cached")
    public ResponseEntity<VideoContent> getContent(@PathVariable String id) {
        return streamingService.getContent(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/content/{id}/watch")
    @Operation(summary = "Start watching", description = "Moves content to high-priority currently-watching cache")
    public ResponseEntity<VideoContent> startWatching(
            @PathVariable String id,
            @RequestParam String userId) {
        VideoContent content = streamingService.startWatching(userId, id);
        return content != null ? ResponseEntity.ok(content) : ResponseEntity.notFound().build();
    }

    @PostMapping("/content/{id}/progress")
    @Operation(summary = "Update watch progress", description = "Keeps content in high-priority cache while watching")
    public ResponseEntity<VideoContent> updateProgress(
            @PathVariable String id,
            @RequestParam String userId,
            @RequestParam int progress) {
        VideoContent content = streamingService.updateProgress(userId, id, progress);
        return content != null ? ResponseEntity.ok(content) : ResponseEntity.notFound().build();
    }

    @GetMapping("/continue-watching/{userId}")
    @Operation(summary = "Get 'Continue Watching' list")
    public ResponseEntity<List<VideoContent>> getContinueWatching(@PathVariable String userId) {
        return ResponseEntity.ok(streamingService.getContinueWatching(userId));
    }

    @GetMapping("/recommendations/{userId}")
    @Operation(summary = "Get personalized recommendations")
    public ResponseEntity<List<VideoContent>> getRecommendations(
            @PathVariable String userId,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(streamingService.getRecommendations(userId, limit));
    }

    @GetMapping("/trending")
    @Operation(summary = "Get trending content")
    public ResponseEntity<List<VideoContent>> getTrending(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(streamingService.getTrending(limit));
    }

    @GetMapping("/inactive")
    @Operation(summary = "Get inactive content (eviction candidates)", description = "Content not watched in 7+ days")
    public ResponseEntity<List<VideoContent>> getInactive(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(streamingService.getInactiveContent(limit));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get cache statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(streamingService.getCacheStats());
    }

    @GetMapping("/demo")
    @Operation(summary = "Demo activity-based caching")
    public ResponseEntity<Map<String, Object>> demo() {
        String userId = "demo-user";

        // Start watching content
        streamingService.startWatching(userId, "content-1");
        streamingService.updateProgress(userId, "content-1", 50);

        // Complete another
        streamingService.startWatching(userId, "content-2");
        streamingService.updateProgress(userId, "content-2", 100);

        return ResponseEntity.ok(Map.of(
                "message", "Started watching content-1 (50%), completed content-2 (100%)",
                "explanation",
                "content-1 in high-priority 'currently watching' cache, content-2 moved to regular cache",
                "continueWatching", streamingService.getContinueWatching(userId),
                "inactiveContent", streamingService.getInactiveContent(5)));
    }
}
