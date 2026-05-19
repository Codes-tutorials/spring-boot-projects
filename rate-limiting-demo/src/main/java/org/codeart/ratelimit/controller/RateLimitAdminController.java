package org.codeart.ratelimit.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.config.RateLimitProperties;
import org.codeart.ratelimit.dto.ApiResponse;
import org.codeart.ratelimit.dto.RateLimitInfo;
import org.codeart.ratelimit.service.ApiKeyService;
import org.codeart.ratelimit.service.RateLimitMetricsService;
import org.codeart.ratelimit.service.RateLimitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin controller for rate limit management and monitoring.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "Rate Limit Admin", description = "Administrative endpoints for rate limit management")
public class RateLimitAdminController {

    private final RateLimitService rateLimitService;
    private final RateLimitMetricsService metricsService;
    private final RateLimitProperties rateLimitProperties;
    private final ApiKeyService apiKeyService;

    /**
     * Get current rate limit configuration
     */
    @GetMapping("/config")
    @Operation(summary = "Get rate limit configuration", description = "Returns current rate limit configuration and tiers")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enabled", rateLimitProperties.isEnabled());
        config.put("storageType", rateLimitProperties.getStorageType());
        config.put("defaultLimit", rateLimitProperties.getDefaultLimit());
        config.put("defaultDuration", rateLimitProperties.getDefaultDuration().toString());

        // Tier information
        Map<String, Map<String, Object>> tierInfo = rateLimitProperties.getTiers().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> Map.of(
                                "requestsPerMinute", e.getValue().getRequestsPerMinute(),
                                "burstCapacity", e.getValue().getBurstCapacity())));
        config.put("tiers", tierInfo);

        // Registered API keys (masked)
        config.put("registeredApiKeys", rateLimitProperties.getApiKeys().size());

        return ResponseEntity.ok(ApiResponse.success(config, "Rate limit configuration"));
    }

    /**
     * Get rate limit status for a specific key
     */
    @GetMapping("/status/{key}")
    @Operation(summary = "Get rate limit status", description = "Get current rate limit status for a specific key")
    public ResponseEntity<ApiResponse<RateLimitInfo>> getStatus(
            @PathVariable String key,
            @RequestParam(defaultValue = "free") String tier) {

        RateLimitInfo status = rateLimitService.getStatus(key, tier);
        return ResponseEntity.ok(ApiResponse.success(status, "Rate limit status for key: " + key));
    }

    /**
     * Get metrics summary
     */
    @GetMapping("/metrics")
    @Operation(summary = "Get rate limit metrics", description = "Get rate limiting metrics summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();

        // Collect metrics for each tier
        for (String tier : rateLimitProperties.getTiers().keySet()) {
            Map<String, Object> tierMetrics = new HashMap<>();
            tierMetrics.put("totalRequests", metricsService.getTotalRequests(tier));
            tierMetrics.put("rejectedRequests", metricsService.getTotalRejected(tier));
            tierMetrics.put("rejectionRate", String.format("%.2f%%", metricsService.getRejectionRate(tier) * 100));
            metrics.put(tier, tierMetrics);
        }

        return ResponseEntity.ok(ApiResponse.success(metrics, "Rate limit metrics"));
    }

    /**
     * Reset rate limit for a specific key
     */
    @DeleteMapping("/limits/{key}")
    @Operation(summary = "Reset rate limit", description = "Reset rate limit for a specific key (admin only)")
    public ResponseEntity<ApiResponse<String>> resetLimit(@PathVariable String key) {
        log.info("Admin request to reset rate limit for key: {}", key);
        rateLimitService.resetLimit(key);
        return ResponseEntity.ok(ApiResponse.success("Rate limit reset", "Rate limit reset for key: " + key));
    }

    /**
     * Get API key information
     */
    @GetMapping("/api-keys/{apiKey}")
    @Operation(summary = "Get API key info", description = "Get information about an API key")
    public ResponseEntity<ApiResponse<?>> getApiKeyInfo(@PathVariable String apiKey) {
        ApiKeyService.ApiKeyInfo info = apiKeyService.getApiKeyInfo(apiKey);

        if (info == null) {
            return ResponseEntity.ok(ApiResponse.error("API key not found or invalid"));
        }

        return ResponseEntity.ok(ApiResponse.success(info, "API key information"));
    }

    /**
     * List all registered tiers
     */
    @GetMapping("/tiers")
    @Operation(summary = "List all tiers", description = "List all registered rate limit tiers with their configurations")
    public ResponseEntity<ApiResponse<Map<String, RateLimitProperties.TierConfig>>> listTiers() {
        return ResponseEntity.ok(ApiResponse.success(
                rateLimitProperties.getTiers(),
                "Available rate limit tiers"));
    }
}
