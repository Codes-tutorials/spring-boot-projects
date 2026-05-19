package org.codeart.circuitbreaker.service;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for monitoring and managing circuit breakers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircuitBreakerMonitorService {

    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Get status of all circuit breakers.
     */
    public List<CircuitBreakerStatus> getAllStatus() {
        return circuitBreakerRegistry.getAllCircuitBreakers()
                .stream()
                .map(this::toStatus)
                .collect(Collectors.toList());
    }

    /**
     * Get status of specific circuit breaker.
     */
    public CircuitBreakerStatus getStatus(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        return toStatus(cb);
    }

    /**
     * Force circuit breaker to OPEN state.
     */
    public CircuitBreakerStatus forceOpen(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        cb.transitionToOpenState();
        log.info("Circuit breaker '{}' forced to OPEN", name);
        return toStatus(cb);
    }

    /**
     * Force circuit breaker to CLOSED state.
     */
    public CircuitBreakerStatus forceClosed(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        cb.transitionToClosedState();
        log.info("Circuit breaker '{}' forced to CLOSED", name);
        return toStatus(cb);
    }

    /**
     * Force circuit breaker to HALF_OPEN state.
     */
    public CircuitBreakerStatus forceHalfOpen(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        cb.transitionToHalfOpenState();
        log.info("Circuit breaker '{}' forced to HALF_OPEN", name);
        return toStatus(cb);
    }

    /**
     * Reset circuit breaker metrics.
     */
    public CircuitBreakerStatus reset(String name) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        cb.reset();
        log.info("Circuit breaker '{}' reset", name);
        return toStatus(cb);
    }

    /**
     * Get recent events for a circuit breaker.
     * Note: This returns a snapshot description, not live events.
     */
    public List<Map<String, Object>> getEvents(String name, int limit) {
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker(name);
        List<Map<String, Object>> events = new ArrayList<>();

        // Get metrics as event summary
        var metrics = cb.getMetrics();
        events.add(Map.of(
                "type", "METRICS_SNAPSHOT",
                "state", cb.getState().name(),
                "failureRate", metrics.getFailureRate(),
                "successfulCalls", metrics.getNumberOfSuccessfulCalls(),
                "failedCalls", metrics.getNumberOfFailedCalls(),
                "notPermittedCalls", metrics.getNumberOfNotPermittedCalls()));

        return events;
    }

    private CircuitBreakerStatus toStatus(CircuitBreaker cb) {
        CircuitBreaker.Metrics metrics = cb.getMetrics();

        return CircuitBreakerStatus.builder()
                .name(cb.getName())
                .state(cb.getState().name())
                .failureRate(metrics.getFailureRate())
                .slowCallRate(metrics.getSlowCallRate())
                .numberOfSuccessfulCalls(metrics.getNumberOfSuccessfulCalls())
                .numberOfFailedCalls(metrics.getNumberOfFailedCalls())
                .numberOfSlowCalls(metrics.getNumberOfSlowCalls())
                .numberOfNotPermittedCalls(metrics.getNumberOfNotPermittedCalls())
                .numberOfBufferedCalls(metrics.getNumberOfBufferedCalls())
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Circuit breaker status DTO.
     */
    @lombok.Builder
    @lombok.Data
    public static class CircuitBreakerStatus {
        private String name;
        private String state; // CLOSED, OPEN, HALF_OPEN
        private float failureRate;
        private float slowCallRate;
        private int numberOfSuccessfulCalls;
        private int numberOfFailedCalls;
        private int numberOfSlowCalls;
        private long numberOfNotPermittedCalls;
        private int numberOfBufferedCalls;
        private Instant timestamp;
    }
}
