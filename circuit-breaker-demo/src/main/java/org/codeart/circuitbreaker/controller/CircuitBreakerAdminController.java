package org.codeart.circuitbreaker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.circuitbreaker.client.ExternalPaymentClient;
import org.codeart.circuitbreaker.service.CircuitBreakerMonitorService;
import org.codeart.circuitbreaker.service.CircuitBreakerMonitorService.CircuitBreakerStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin controller for monitoring and controlling circuit breakers.
 */
@RestController
@RequestMapping("/api/admin/circuit-breaker")
@RequiredArgsConstructor
@Tag(name = "Circuit Breaker Admin", description = "Monitor and control circuit breakers")
public class CircuitBreakerAdminController {

    private final CircuitBreakerMonitorService monitorService;
    private final ExternalPaymentClient paymentClient;

    @GetMapping("/status")
    @Operation(summary = "Get status of all circuit breakers")
    public ResponseEntity<List<CircuitBreakerStatus>> getAllStatus() {
        return ResponseEntity.ok(monitorService.getAllStatus());
    }

    @GetMapping("/status/{name}")
    @Operation(summary = "Get status of specific circuit breaker")
    public ResponseEntity<CircuitBreakerStatus> getStatus(@PathVariable String name) {
        return ResponseEntity.ok(monitorService.getStatus(name));
    }

    @PostMapping("/{name}/open")
    @Operation(summary = "Force circuit breaker to OPEN state")
    public ResponseEntity<CircuitBreakerStatus> forceOpen(@PathVariable String name) {
        return ResponseEntity.ok(monitorService.forceOpen(name));
    }

    @PostMapping("/{name}/close")
    @Operation(summary = "Force circuit breaker to CLOSED state")
    public ResponseEntity<CircuitBreakerStatus> forceClosed(@PathVariable String name) {
        return ResponseEntity.ok(monitorService.forceClosed(name));
    }

    @PostMapping("/{name}/half-open")
    @Operation(summary = "Force circuit breaker to HALF_OPEN state")
    public ResponseEntity<CircuitBreakerStatus> forceHalfOpen(@PathVariable String name) {
        return ResponseEntity.ok(monitorService.forceHalfOpen(name));
    }

    @PostMapping("/{name}/reset")
    @Operation(summary = "Reset circuit breaker metrics")
    public ResponseEntity<CircuitBreakerStatus> reset(@PathVariable String name) {
        return ResponseEntity.ok(monitorService.reset(name));
    }

    @GetMapping("/{name}/events")
    @Operation(summary = "Get recent events for circuit breaker")
    public ResponseEntity<List<Map<String, Object>>> getEvents(
            @PathVariable String name,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(monitorService.getEvents(name, limit));
    }

    // External client configuration

    @PostMapping("/simulate/failure-rate")
    @Operation(summary = "Set failure rate for payment service (0.0-1.0)")
    public ResponseEntity<Map<String, Object>> setFailureRate(@RequestParam double rate) {
        paymentClient.setFailureRate(rate);
        return ResponseEntity.ok(Map.of(
                "failureRate", rate,
                "message", "Failure rate set to " + (rate * 100) + "%"));
    }

    @PostMapping("/simulate/latency")
    @Operation(summary = "Set latency for payment service (ms)")
    public ResponseEntity<Map<String, Object>> setLatency(@RequestParam long ms) {
        paymentClient.setLatency(ms);
        return ResponseEntity.ok(Map.of(
                "latencyMs", ms,
                "message", "Latency set to " + ms + "ms"));
    }

    @PostMapping("/simulate/timeout")
    @Operation(summary = "Enable/disable timeout simulation")
    public ResponseEntity<Map<String, Object>> setTimeout(@RequestParam boolean enable) {
        paymentClient.setSimulateTimeout(enable);
        return ResponseEntity.ok(Map.of(
                "timeout", enable,
                "message", "Timeout simulation " + (enable ? "enabled" : "disabled")));
    }

    @GetMapping("/simulate/config")
    @Operation(summary = "Get current simulation configuration")
    public ResponseEntity<ExternalPaymentClient.ClientConfig> getSimulationConfig() {
        return ResponseEntity.ok(paymentClient.getConfig());
    }

    @PostMapping("/simulate/reset")
    @Operation(summary = "Reset simulation to defaults")
    public ResponseEntity<Map<String, String>> resetSimulation() {
        paymentClient.setFailureRate(0.0);
        paymentClient.setLatency(100);
        paymentClient.setSimulateTimeout(false);
        paymentClient.resetCallCount();
        return ResponseEntity.ok(Map.of("message", "Simulation reset to defaults"));
    }
}
