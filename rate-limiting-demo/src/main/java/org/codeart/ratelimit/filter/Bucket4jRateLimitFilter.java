package org.codeart.ratelimit.filter;

import io.micrometer.core.instrument.Timer;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.config.RateLimitProperties;
import org.codeart.ratelimit.dto.RateLimitInfo;
import org.codeart.ratelimit.resolver.ApiKeyResolver;
import org.codeart.ratelimit.resolver.IpKeyResolver;
import org.codeart.ratelimit.service.ApiKeyService;
import org.codeart.ratelimit.service.RateLimitMetricsService;
import org.codeart.ratelimit.service.RateLimitService;
import org.codeart.ratelimit.service.RedisRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Global rate limit filter supporting multiple backends:
 * - Bucket4j (in-memory or Redis)
 * - Redis INCR (fixed window)
 * - Redis Lua (sliding window)
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class Bucket4jRateLimitFilter implements Filter {

    private final RateLimitService rateLimitService;
    private final RateLimitProperties rateLimitProperties;
    private final IpKeyResolver ipKeyResolver;
    private final ApiKeyResolver apiKeyResolver;
    private final ApiKeyService apiKeyService;
    private final RateLimitMetricsService metricsService;

    // Optional Redis-only rate limiters
    private final RedisRateLimiter redisRateLimiter;

    @Autowired
    public Bucket4jRateLimitFilter(
            RateLimitService rateLimitService,
            RateLimitProperties rateLimitProperties,
            IpKeyResolver ipKeyResolver,
            ApiKeyResolver apiKeyResolver,
            ApiKeyService apiKeyService,
            RateLimitMetricsService metricsService,
            @Autowired(required = false) RedisRateLimiter redisRateLimiter) {
        this.rateLimitService = rateLimitService;
        this.rateLimitProperties = rateLimitProperties;
        this.ipKeyResolver = ipKeyResolver;
        this.apiKeyResolver = apiKeyResolver;
        this.apiKeyService = apiKeyService;
        this.metricsService = metricsService;
        this.redisRateLimiter = redisRateLimiter;

        String backend = redisRateLimiter != null ? redisRateLimiter.getAlgorithmName() : "Bucket4j";
        log.info("Rate limit filter initialized with backend: {}", backend);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        if (!rateLimitProperties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Skip rate limiting for actuator and swagger endpoints
        String path = httpRequest.getRequestURI();
        if (shouldSkip(path)) {
            chain.doFilter(request, response);
            return;
        }

        Timer.Sample timerSample = metricsService.startTimer();

        // Resolve rate limit key and tier
        String apiKeyHeader = httpRequest.getHeader(ApiKeyResolver.API_KEY_HEADER);
        String tier = apiKeyService.getTierForApiKey(apiKeyHeader);

        // Use API key for rate limiting if provided, otherwise use IP
        String rateLimitKey;
        if (apiKeyHeader != null && !apiKeyHeader.isBlank()) {
            rateLimitKey = apiKeyResolver.resolve(httpRequest);
        } else {
            rateLimitKey = ipKeyResolver.resolve(httpRequest);
        }

        // Check rate limit using appropriate backend
        RateLimitResult result = checkRateLimit(rateLimitKey, tier);

        // Record latency metric
        metricsService.recordLatency(timerSample, tier, result.allowed);

        // Add rate limit headers to response
        addRateLimitHeaders(httpResponse, result.info);

        if (result.allowed) {
            chain.doFilter(request, response);
        } else {
            handleRateLimitExceeded(httpRequest, httpResponse, result.info, rateLimitKey);
        }
    }

    /**
     * Check rate limit using the appropriate backend.
     */
    private RateLimitResult checkRateLimit(String key, String tier) {
        if (redisRateLimiter != null) {
            // Use Redis-only rate limiter (INCR or Lua)
            RedisRateLimiter.RateLimitResult result = redisRateLimiter.tryConsume(key, tier);
            return new RateLimitResult(result.allowed(), result.info());
        } else {
            // Use Bucket4j (in-memory or Redis-backed)
            RateLimitService.RateLimitResult result = rateLimitService.tryConsume(key, tier);
            return new RateLimitResult(result.allowed(), result.info());
        }
    }

    private boolean shouldSkip(String path) {
        return path.startsWith("/actuator") ||
                path.startsWith("/swagger-ui") ||
                path.startsWith("/api-docs") ||
                path.startsWith("/v3/api-docs") ||
                path.equals("/favicon.ico");
    }

    private void addRateLimitHeaders(HttpServletResponse response, RateLimitInfo info) {
        response.setHeader("X-RateLimit-Limit", String.valueOf(info.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(info.getRemaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(info.getResetAt()));
        response.setHeader("X-RateLimit-Tier", info.getTier());
    }

    private void handleRateLimitExceeded(
            HttpServletRequest request,
            HttpServletResponse response,
            RateLimitInfo info,
            String key) throws IOException {

        log.warn("Rate limit exceeded for key: {}, path: {}, tier: {}",
                key, request.getRequestURI(), info.getTier());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(info.getRetryAfterSeconds()));

        String errorJson = String.format("""
                {
                    "error": "RATE_LIMIT_EXCEEDED",
                    "status": 429,
                    "message": "Too many requests. Please retry after %d seconds.",
                    "timestamp": "%s",
                    "path": "%s",
                    "rateLimit": {
                        "limit": %d,
                        "remaining": %d,
                        "resetAt": %d,
                        "retryAfterSeconds": %d,
                        "tier": "%s"
                    }
                }
                """,
                info.getRetryAfterSeconds(),
                Instant.now().toString(),
                request.getRequestURI(),
                info.getLimit(),
                info.getRemaining(),
                info.getResetAt(),
                info.getRetryAfterSeconds(),
                info.getTier());

        response.getWriter().write(errorJson);
    }

    /**
     * Internal result record for unified handling.
     */
    private record RateLimitResult(boolean allowed, RateLimitInfo info) {
    }
}
