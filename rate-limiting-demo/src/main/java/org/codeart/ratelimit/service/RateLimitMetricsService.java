package org.codeart.ratelimit.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for rate limiting metrics using Micrometer.
 */
@Slf4j
@Service
public class RateLimitMetricsService {

    private final MeterRegistry meterRegistry;

    private static final String METRIC_REQUESTS_TOTAL = "rate_limit_requests_total";
    private static final String METRIC_REJECTED_TOTAL = "rate_limit_rejected_total";
    private static final String METRIC_LATENCY = "rate_limit_check_latency";

    private final ConcurrentHashMap<String, Counter> requestCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> rejectedCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> currentRequests = new ConcurrentHashMap<>();

    public RateLimitMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("RateLimitMetricsService initialized");
    }

    /**
     * Record a rate limit request.
     * 
     * @param tier    the rate limit tier
     * @param allowed whether the request was allowed
     */
    public void recordRequest(String tier, boolean allowed) {
        // Increment total requests counter
        getRequestCounter(tier).increment();

        // Track current active requests
        currentRequests.computeIfAbsent(tier, k -> new AtomicLong(0)).incrementAndGet();

        if (!allowed) {
            // Increment rejected counter
            getRejectedCounter(tier).increment();
        }
    }

    /**
     * Record the latency of a rate limit check.
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    public void recordLatency(Timer.Sample sample, String tier, boolean allowed) {
        sample.stop(Timer.builder(METRIC_LATENCY)
                .tags(List.of(
                        Tag.of("tier", tier),
                        Tag.of("allowed", String.valueOf(allowed))))
                .description("Rate limit check latency")
                .register(meterRegistry));
    }

    /**
     * Get total requests for a tier.
     */
    public double getTotalRequests(String tier) {
        Counter counter = requestCounters.get(tier);
        return counter != null ? counter.count() : 0;
    }

    /**
     * Get total rejected requests for a tier.
     */
    public double getTotalRejected(String tier) {
        Counter counter = rejectedCounters.get(tier);
        return counter != null ? counter.count() : 0;
    }

    /**
     * Get rejection rate for a tier.
     */
    public double getRejectionRate(String tier) {
        double total = getTotalRequests(tier);
        if (total == 0) {
            return 0.0;
        }
        return getTotalRejected(tier) / total;
    }

    private Counter getRequestCounter(String tier) {
        return requestCounters.computeIfAbsent(tier, t -> Counter.builder(METRIC_REQUESTS_TOTAL)
                .tag("tier", t)
                .description("Total rate limit requests")
                .register(meterRegistry));
    }

    private Counter getRejectedCounter(String tier) {
        return rejectedCounters.computeIfAbsent(tier, t -> Counter.builder(METRIC_REJECTED_TOTAL)
                .tag("tier", t)
                .description("Total rejected rate limit requests")
                .register(meterRegistry));
    }
}
