package org.codeart.cache.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.cache.model.SocialPost;
import org.codeart.cache.service.SocialFeedService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Social Media Feed Controller - LRU with Interaction Priority
 */
@RestController
@RequestMapping("/api/social")
@RequiredArgsConstructor
@Tag(name = "Social Feed (LRU)", description = "Recent interactions cached, old posts evicted")
public class SocialFeedController {

    private final SocialFeedService feedService;

    @GetMapping("/feed/{userId}")
    @Operation(summary = "Get user's feed", description = "Feed of posts from followed users (cached)")
    public ResponseEntity<List<SocialPost>> getFeed(
            @PathVariable String userId,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(feedService.getFeed(userId, limit));
    }

    @GetMapping("/post/{id}")
    @Operation(summary = "Get post by ID", description = "LRU: Accessed posts stay cached longer")
    public ResponseEntity<SocialPost> getPost(@PathVariable String id) {
        return feedService.getPost(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/post")
    @Operation(summary = "Create a post")
    public ResponseEntity<SocialPost> createPost(@RequestBody PostRequest request) {
        return ResponseEntity.ok(feedService.createPost(
                request.userId(), request.content(), request.type()));
    }

    @PostMapping("/post/{id}/like")
    @Operation(summary = "Like a post", description = "Liking extends cache TTL (keeps post in cache)")
    public ResponseEntity<SocialPost> likePost(
            @PathVariable String id,
            @RequestParam String userId) {
        return ResponseEntity.ok(feedService.likePost(id, userId));
    }

    @GetMapping("/stale")
    @Operation(summary = "Get stale posts (eviction candidates)")
    public ResponseEntity<List<SocialPost>> getStalePosts(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(feedService.getStalePosts(limit));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get cache statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(feedService.getCacheStats());
    }

    @GetMapping("/demo")
    @Operation(summary = "Demo LRU + interaction behavior")
    public ResponseEntity<Map<String, Object>> demo() {
        // Access and interact with posts
        feedService.getPost("post-1");
        feedService.likePost("post-1", "demo-user");
        feedService.getPost("post-2");
        // post-3 not accessed - will be evicted first

        return ResponseEntity.ok(Map.of(
                "message", "post-1 accessed & liked (high priority), post-2 just accessed, post-3 untouched",
                "explanation", "LRU evicts post-3 first (not accessed), then post-2 (accessed but not interacted)",
                "stale_posts", feedService.getStalePosts(5)));
    }

    public record PostRequest(String userId, String content, SocialPost.PostType type) {
    }
}
