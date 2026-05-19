package org.codeart.ratelimit.controller;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.annotation.RateLimit;
import org.codeart.ratelimit.dto.ApiResponse;
import org.codeart.ratelimit.dto.ErrorResponse;
import org.codeart.ratelimit.resolver.KeyResolver.KeyType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Demo controller showcasing various rate limiting strategies.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Rate Limiting Demo", description = "Endpoints demonstrating different rate limiting strategies")
public class RateLimitDemoController {

    /**
     * Public endpoint - Rate limited by global filter (IP-based)
     * Uses the global Bucket4j filter with tier based on X-Api-Key header
     */
    @GetMapping("/public")
    @Operation(summary = "Public endpoint with global rate limiting", description = "Rate limited by the global Bucket4j filter. Uses IP-based limiting for anonymous requests, "
            +
            "or tier-based limiting when X-Api-Key header is provided.")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Request allowed", headers = {
                    @Header(name = "X-RateLimit-Limit", description = "Maximum requests allowed"),
                    @Header(name = "X-RateLimit-Remaining", description = "Remaining requests"),
                    @Header(name = "X-RateLimit-Reset", description = "Unix timestamp when limit resets")
            }),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Too many requests", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> publicEndpoint(
            @Parameter(description = "API Key for tier-based rate limiting") @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {

        Map<String, Object> data = Map.of(
                "endpoint", "public",
                "strategy", "Global Bucket4j Filter",
                "keyType", apiKey != null ? "API_KEY" : "IP",
                "timestamp", Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.success(data, "Request processed successfully"));
    }

    /**
     * Protected endpoint - Rate limited by Resilience4j annotation
     * Uses method-level rate limiting with backendA configuration
     */
    @GetMapping("/protected")
    @RateLimiter(name = "backendA")
    @Operation(summary = "Protected endpoint with Resilience4j rate limiting", description = "Uses Resilience4j @RateLimiter annotation with 'backendA' configuration (5 requests/minute)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> protectedEndpoint() {
        Map<String, Object> data = Map.of(
                "endpoint", "protected",
                "strategy", "Resilience4j @RateLimiter",
                "config", "backendA (5 req/min)",
                "timestamp", Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.success(data, "Protected resource accessed"));
    }

    /**
     * Relaxed endpoint - Higher rate limit using Resilience4j
     */
    @GetMapping("/relaxed")
    @RateLimiter(name = "backendB")
    @Operation(summary = "Relaxed endpoint with higher rate limit", description = "Uses Resilience4j @RateLimiter with 'backendB' configuration (100 requests/minute)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> relaxedEndpoint() {
        Map<String, Object> data = Map.of(
                "endpoint", "relaxed",
                "strategy", "Resilience4j @RateLimiter",
                "config", "backendB (100 req/min)",
                "timestamp", Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.success(data, "Relaxed resource accessed"));
    }

    /**
     * Custom rate limit using @RateLimit annotation
     * 3 requests per 10 seconds, IP-based
     */
    @GetMapping("/custom")
    @RateLimit(limit = 3, duration = 10, timeUnit = TimeUnit.SECONDS, keyType = KeyType.IP, name = "customEndpoint")
    @Operation(summary = "Custom endpoint with @RateLimit annotation", description = "Uses custom @RateLimit annotation (3 requests per 10 seconds, IP-based)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> customEndpoint() {
        Map<String, Object> data = Map.of(
                "endpoint", "custom",
                "strategy", "Custom @RateLimit annotation",
                "config", "3 requests / 10 seconds",
                "timestamp", Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.success(data, "Custom rate-limited resource accessed"));
    }

    /**
     * API Key based rate limit using @RateLimit annotation
     */
    @GetMapping("/api-key-limited")
    @RateLimit(limit = 5, duration = 1, timeUnit = TimeUnit.MINUTES, keyType = KeyType.API_KEY, name = "apiKeyEndpoint")
    @Operation(summary = "API Key limited endpoint", description = "Rate limited per API key (5 requests per minute per key)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> apiKeyLimitedEndpoint(
            @RequestHeader(value = "X-Api-Key", required = false) String apiKey) {
        Map<String, Object> data = Map.of(
                "endpoint", "api-key-limited",
                "strategy", "Custom @RateLimit with API_KEY",
                "apiKey", apiKey != null ? maskApiKey(apiKey) : "none",
                "timestamp", Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.success(data, "API key rate-limited resource accessed"));
    }

    /**
     * Simulate a slow endpoint (demonstrates rate limiting doesn't affect
     * processing time)
     */
    @GetMapping("/slow")
    @RateLimit(limit = 2, duration = 30, timeUnit = TimeUnit.SECONDS, name = "slowEndpoint")
    @Operation(summary = "Slow endpoint simulation", description = "Simulates a slow operation with strict rate limiting (2 req/30 seconds)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> slowEndpoint() throws InterruptedException {
        // Simulate slow processing
        Thread.sleep(1000);

        Map<String, Object> data = Map.of(
                "endpoint", "slow",
                "strategy", "Custom @RateLimit",
                "processingTime", "1 second",
                "timestamp", Instant.now().toString());

        return ResponseEntity.ok(ApiResponse.success(data, "Slow operation completed"));
    }

    /**
     * Health check endpoint - not rate limited
     */
    @GetMapping("/health")
    @Operation(summary = "Health check (not rate limited)")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString()));
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
