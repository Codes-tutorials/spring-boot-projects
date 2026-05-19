package org.codeart.circuitbreaker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.circuitbreaker.service.PaymentService;
import org.codeart.circuitbreaker.service.PaymentService.PaymentResult;
import org.codeart.circuitbreaker.service.CircuitBreakerMonitorService;
import org.codeart.circuitbreaker.client.ExternalPaymentClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Demo controller to showcase circuit breaker behavior.
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Tag(name = "Demo", description = "Demonstrate circuit breaker patterns")
public class DemoController {

    private final PaymentService paymentService;
    private final CircuitBreakerMonitorService monitorService;
    private final ExternalPaymentClient paymentClient;

    @GetMapping("/circuit-breaker-lifecycle")
    @Operation(summary = "Demo circuit breaker lifecycle", description = "Shows CLOSED -> OPEN -> HALF_OPEN -> CLOSED transitions")
    public ResponseEntity<Map<String, Object>> demoLifecycle() {
        List<Map<String, Object>> results = new ArrayList<>();

        // Reset to clean state
        paymentClient.setFailureRate(0.0);
        monitorService.reset("paymentService");

        // Step 1: Normal calls (CLOSED)
        results.add(Map.of("phase", "1. Normal calls (CLOSED)",
                "action", "Making 5 successful calls"));
        for (int i = 0; i < 5; i++) {
            try {
                PaymentResult r = paymentService.processPayment("order-" + i, 100.0);
                results.add(Map.of("call", i + 1, "status", r.getStatus(), "source", r.getSource()));
            } catch (Exception e) {
                results.add(Map.of("call", i + 1, "error", e.getMessage()));
            }
        }
        results.add(Map.of("circuitState", monitorService.getStatus("paymentService").getState()));

        // Step 2: Simulate failures (will trigger OPEN)
        paymentClient.setFailureRate(1.0); // 100% failure
        results.add(Map.of("phase", "2. Simulating failures (trigger OPEN)",
                "action", "Making 5 failing calls"));
        for (int i = 0; i < 5; i++) {
            try {
                PaymentResult r = paymentService.processPayment("order-fail-" + i, 100.0);
                results.add(Map.of("call", i + 1, "status", r.getStatus(), "source", r.getSource()));
            } catch (Exception e) {
                results.add(Map.of("call", i + 1, "error", e.getMessage()));
            }
        }
        results.add(Map.of("circuitState", monitorService.getStatus("paymentService").getState()));

        // Step 3: Calls rejected (OPEN)
        results.add(Map.of("phase", "3. Calls rejected (OPEN)",
                "action", "Calls should use fallback"));
        for (int i = 0; i < 3; i++) {
            PaymentResult r = paymentService.processPayment("order-rejected-" + i, 100.0);
            results.add(Map.of("call", i + 1, "status", r.getStatus(), "source", r.getSource()));
        }

        // Reset
        paymentClient.setFailureRate(0.0);

        return ResponseEntity.ok(Map.of(
                "demo", "Circuit Breaker Lifecycle",
                "results", results,
                "finalState", monitorService.getStatus("paymentService")));
    }

    @GetMapping("/retry-with-recovery")
    @Operation(summary = "Demo retry with eventual success", description = "Service fails twice then succeeds (retries handle it)")
    public ResponseEntity<Map<String, Object>> demoRetry() {
        // Configure to fail 50% of the time
        paymentClient.setFailureRate(0.5);
        monitorService.reset("paymentService");

        List<Map<String, Object>> results = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            long start = System.currentTimeMillis();
            try {
                PaymentResult r = paymentService.processPayment("retry-order-" + i, 100.0);
                long elapsed = System.currentTimeMillis() - start;
                results.add(Map.of(
                        "call", i + 1,
                        "status", r.getStatus(),
                        "source", r.getSource(),
                        "latencyMs", elapsed));
            } catch (Exception e) {
                results.add(Map.of("call", i + 1, "error", e.getMessage()));
            }
        }

        paymentClient.setFailureRate(0.0);

        return ResponseEntity.ok(Map.of(
                "demo", "Retry with Recovery",
                "callCount", paymentClient.getCallCount(),
                "results", results));
    }

    @GetMapping("/cascade-prevention")
    @Operation(summary = "Demo cascade failure prevention", description = "Shows how circuit breaker prevents overwhelming a failed service")
    public ResponseEntity<Map<String, Object>> demoCascadePrevention() {
        // Simulate service down
        paymentClient.setFailureRate(1.0);
        monitorService.reset("paymentService");
        paymentClient.resetCallCount();

        List<String> callResults = new ArrayList<>();
        int actualCallsMade = 0;
        int fallbacksUsed = 0;

        // Simulate 100 incoming requests
        for (int i = 0; i < 100; i++) {
            PaymentResult r = paymentService.processPayment("cascade-" + i, 100.0);
            if ("FALLBACK".equals(r.getSource())) {
                fallbacksUsed++;
            }
        }

        actualCallsMade = paymentClient.getCallCount();

        // Without circuit breaker: 100 calls would hit the failing service
        // With circuit breaker: Only ~10 calls hit service, rest use fallback

        paymentClient.setFailureRate(0.0);

        return ResponseEntity.ok(Map.of(
                "demo", "Cascade Failure Prevention",
                "totalRequests", 100,
                "actualServiceCalls", actualCallsMade,
                "fallbacksUsed", fallbacksUsed,
                "callsSaved", 100 - actualCallsMade,
                "explanation", "Circuit breaker prevented " + (100 - actualCallsMade) +
                        " calls from hitting the failed service",
                "circuitState", monitorService.getStatus("paymentService").getState()));
    }

    @GetMapping("/all-patterns")
    @Operation(summary = "Show all Resilience4j patterns in action")
    public ResponseEntity<Map<String, Object>> showAllPatterns() {
        return ResponseEntity.ok(Map.of(
                "patterns", List.of(
                        Map.of(
                                "name", "Circuit Breaker",
                                "status", monitorService.getStatus("paymentService"),
                                "description", "Prevents calls when service is failing"),
                        Map.of(
                                "name", "Retry",
                                "description", "Automatically retries failed calls with backoff",
                                "config", "maxAttempts=3, waitDuration=500ms, exponentialBackoff=2x"),
                        Map.of(
                                "name", "Rate Limiter",
                                "description", "Limits calls per second",
                                "config", "5 calls/second for paymentService"),
                        Map.of(
                                "name", "Bulkhead",
                                "description", "Limits concurrent calls (thread isolation)",
                                "config", "maxConcurrentCalls=5 for paymentService"),
                        Map.of(
                                "name", "Time Limiter",
                                "description", "Timeout protection",
                                "config", "timeout=2s for paymentService")),
                "endpoints", Map.of(
                        "payment", "/api/payment/process",
                        "monitor", "/api/admin/circuit-breaker/status",
                        "actuator", "/actuator/health")));
    }
}
