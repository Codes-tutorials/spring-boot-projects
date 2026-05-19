package org.codeart.kafka.producer.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.kafka.model.LogEvent;
import org.codeart.kafka.producer.config.LogProducerProperties;
import org.codeart.kafka.producer.service.LogProducerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Demo controller for sending logs via REST API.
 * In production, logs would be sent automatically via Logback appender.
 */
@Slf4j
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogProducerService logProducerService;
    private final LogProducerProperties properties;

    /**
     * Send a simple log message
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> sendLog(@RequestBody LogRequest request) {
        logProducerService.sendLog(request.level(), request.message());

        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "instanceId", properties.getInstanceId()));
    }

    /**
     * Send a log with trace context
     */
    @PostMapping("/traced")
    public ResponseEntity<Map<String, String>> sendTracedLog(
            @RequestBody LogRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId,
            @RequestHeader(value = "X-Span-Id", required = false) String spanId,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId) {

        // Generate trace IDs if not provided
        if (traceId == null)
            traceId = UUID.randomUUID().toString();
        if (spanId == null)
            spanId = UUID.randomUUID().toString().substring(0, 16);
        if (correlationId == null)
            correlationId = UUID.randomUUID().toString();

        logProducerService.sendLog(
                request.level(),
                request.message(),
                traceId,
                spanId,
                correlationId);

        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "traceId", traceId,
                "correlationId", correlationId));
    }

    /**
     * Send a full log event
     */
    @PostMapping("/event")
    public ResponseEntity<Map<String, String>> sendEvent(@RequestBody LogEvent event) {
        logProducerService.sendLog(event);

        return ResponseEntity.ok(Map.of(
                "status", "sent",
                "serviceName", event.getServiceName() != null ? event.getServiceName() : "auto"));
    }

    /**
     * Simulate error log
     */
    @PostMapping("/error")
    public ResponseEntity<Map<String, String>> sendErrorLog(@RequestBody ErrorRequest request) {
        Exception simulatedException = new RuntimeException(request.errorMessage());
        logProducerService.sendError(request.message(), simulatedException);

        return ResponseEntity.ok(Map.of(
                "status", "error_logged"));
    }

    /**
     * Health check
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "instanceId", properties.getInstanceId(),
                "environment", properties.getEnvironment()));
    }

    public record LogRequest(String level, String message) {
    }

    public record ErrorRequest(String message, String errorMessage) {
    }
}
