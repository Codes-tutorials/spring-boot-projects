package org.codeart.kafka.consumer.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.codeart.kafka.consumer.config.LogConsumerProperties;
import org.codeart.kafka.model.LogEvent;
import org.codeart.kafka.model.KafkaTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Production-ready log consumer service with:
 * - Batch consumption for throughput
 * - Metrics per service and level
 * - Multi-instance support with partition distribution
 */
@Slf4j
@Service
public class LogConsumerService {

    private final LogConsumerProperties properties;
    private final Counter logsReceivedCounter;
    private final Counter errorLogsCounter;

    // Statistics per service (for demo)
    private final Map<String, AtomicLong> logCountByService = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> logCountByLevel = new ConcurrentHashMap<>();

    public LogConsumerService(LogConsumerProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;

        // Register metrics
        this.logsReceivedCounter = Counter.builder("logs.received.total")
                .description("Total logs received")
                .tag("instance", properties.getInstanceId())
                .register(meterRegistry);

        this.errorLogsCounter = Counter.builder("logs.received.errors")
                .description("Error level logs received")
                .tag("instance", properties.getInstanceId())
                .register(meterRegistry);

        log.info("LogConsumerService initialized with instance ID: {}", properties.getInstanceId());
    }

    /**
     * Batch listener for system logs.
     * Receives logs in batches for better throughput.
     */
    @KafkaListener(topics = KafkaTopics.SYSTEM_LOGS, groupId = "${spring.kafka.consumer.group-id}", containerFactory = "kafkaListenerContainerFactory")
    public void consumeLogs(
            @Payload List<LogEvent> logs,
            @Header(KafkaHeaders.RECEIVED_PARTITION) List<Integer> partitions,
            @Header(KafkaHeaders.OFFSET) List<Long> offsets) {

        log.debug("Received batch of {} logs from partitions: {}", logs.size(), partitions);

        for (int i = 0; i < logs.size(); i++) {
            LogEvent logEvent = logs.get(i);
            int partition = partitions.get(i);
            long offset = offsets.get(i);

            processLog(logEvent, partition, offset);
        }

        log.info("Processed batch of {} logs by instance {}", logs.size(), properties.getInstanceId());
    }

    /**
     * Process individual log event.
     */
    private void processLog(LogEvent event, int partition, long offset) {
        // Increment counters
        logsReceivedCounter.increment();

        String serviceName = event.getServiceName() != null ? event.getServiceName() : "unknown";
        String level = event.getLevel() != null ? event.getLevel() : "INFO";

        logCountByService.computeIfAbsent(serviceName, k -> new AtomicLong()).incrementAndGet();
        logCountByLevel.computeIfAbsent(level, k -> new AtomicLong()).incrementAndGet();

        if ("ERROR".equalsIgnoreCase(level)) {
            errorLogsCounter.increment();
            log.warn("[{}] ERROR from {}: {}", partition, serviceName, event.getMessage());

            // Log exception details if present
            if (event.getException() != null) {
                log.warn("  Exception: {} - {}",
                        event.getException().getType(),
                        event.getException().getMessage());
            }
        } else {
            log.info("[P{}:O{}] {} | {} | {} | {}",
                    partition,
                    offset,
                    event.getTimestamp(),
                    serviceName,
                    level,
                    truncate(event.getMessage(), 100));
        }

        // Log trace info if present
        if (event.getTraceId() != null) {
            log.debug("  TraceId: {}, SpanId: {}, CorrelationId: {}",
                    event.getTraceId(), event.getSpanId(), event.getCorrelationId());
        }
    }

    /**
     * Get statistics for monitoring.
     */
    public Map<String, Object> getStats() {
        return Map.of(
                "instanceId", properties.getInstanceId(),
                "totalReceived", logsReceivedCounter.count(),
                "errorLogs", errorLogsCounter.count(),
                "byService", logCountByService,
                "byLevel", logCountByLevel);
    }

    private String truncate(String text, int maxLength) {
        if (text == null)
            return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}
