package org.codeart.kafka.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Production-ready log event for centralized logging via Kafka.
 * Supports distributed tracing, multi-service, and multi-instance deployments.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogEvent {

    /**
     * Name of the service that generated this log
     */
    private String serviceName;

    /**
     * Unique instance ID (for multiple instances of same service)
     */
    private String instanceId;

    /**
     * Log level: TRACE, DEBUG, INFO, WARN, ERROR
     */
    private String level;

    /**
     * Log message
     */
    private String message;

    /**
     * Logger name (typically class name)
     */
    private String logger;

    /**
     * Thread name
     */
    private String thread;

    /**
     * Timestamp of the log event
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ", timezone = "UTC")
    private Instant timestamp;

    /**
     * Distributed trace ID (from Sleuth/OpenTelemetry)
     */
    private String traceId;

    /**
     * Span ID for distributed tracing
     */
    private String spanId;

    /**
     * Correlation ID for request tracking
     */
    private String correlationId;

    /**
     * Exception details (if any)
     */
    private ExceptionInfo exception;

    /**
     * Additional metadata (MDC context, custom fields)
     */
    private Map<String, String> metadata;

    /**
     * Host/container name where the service is running
     */
    private String host;

    /**
     * Environment (dev, staging, prod)
     */
    private String environment;

    /**
     * Exception information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExceptionInfo {
        private String type;
        private String message;
        private String stackTrace;
    }

    /**
     * Create a simple log event
     */
    public static LogEvent of(String serviceName, String level, String message) {
        return LogEvent.builder()
                .serviceName(serviceName)
                .level(level)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a log event with trace context
     */
    public static LogEvent withTrace(String serviceName, String level, String message, 
                                      String traceId, String spanId) {
        return LogEvent.builder()
                .serviceName(serviceName)
                .level(level)
                .message(message)
                .traceId(traceId)
                .spanId(spanId)
                .timestamp(Instant.now())
                .build();
    }
}
