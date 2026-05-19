package org.codeart.kafka.producer.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.kafka.model.LogEvent;
import org.codeart.kafka.model.LogLevel;
import org.codeart.kafka.producer.config.LogProducerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Production-ready log producer service with:
 * - Dynamic service name
 * - Instance ID for multi-instance support
 * - Correlation ID / Trace ID propagation
 * - Error handling with callbacks
 * - Metrics integration
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LogProducerService {

    private final KafkaTemplate<String, LogEvent> kafkaTemplate;
    private final LogProducerProperties properties;

    @Value("${spring.application.name}")
    private String serviceName;

    private String hostname;

    /**
     * Send a log event to Kafka.
     */
    public CompletableFuture<SendResult<String, LogEvent>> sendLog(LogEvent event) {
        // Enrich event with producer context
        enrichEvent(event);

        String topic = properties.getSystemLogsTopic();
        String key = event.getServiceName();

        log.debug("Sending log to topic '{}', key '{}': {}", topic, key, event.getMessage());

        CompletableFuture<SendResult<String, LogEvent>> future = kafkaTemplate.send(topic, key, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to send log to Kafka: {}", event.getMessage(), ex);
            } else {
                log.debug("Log sent successfully to partition {} offset {}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });

        return future;
    }

    /**
     * Send a simple log message.
     */
    public CompletableFuture<SendResult<String, LogEvent>> sendLog(String level, String message) {
        LogEvent event = LogEvent.builder()
                .level(level)
                .message(message)
                .build();
        return sendLog(event);
    }

    /**
     * Send a log with trace context.
     */
    public CompletableFuture<SendResult<String, LogEvent>> sendLog(
            String level, String message, String traceId, String spanId, String correlationId) {
        LogEvent event = LogEvent.builder()
                .level(level)
                .message(message)
                .traceId(traceId)
                .spanId(spanId)
                .correlationId(correlationId)
                .build();
        return sendLog(event);
    }

    /**
     * Send a log with metadata.
     */
    public CompletableFuture<SendResult<String, LogEvent>> sendLog(
            String level, String message, Map<String, String> metadata) {
        LogEvent event = LogEvent.builder()
                .level(level)
                .message(message)
                .metadata(metadata)
                .build();
        return sendLog(event);
    }

    /**
     * Send an error log with exception details.
     */
    public CompletableFuture<SendResult<String, LogEvent>> sendError(String message, Throwable throwable) {
        LogEvent.ExceptionInfo exInfo = LogEvent.ExceptionInfo.builder()
                .type(throwable.getClass().getName())
                .message(throwable.getMessage())
                .stackTrace(getStackTraceString(throwable))
                .build();

        LogEvent event = LogEvent.builder()
                .level(LogLevel.ERROR.name())
                .message(message)
                .exception(exInfo)
                .build();

        return sendLog(event);
    }

    /**
     * Enrich the event with producer context.
     */
    private void enrichEvent(LogEvent event) {
        if (event.getServiceName() == null) {
            event.setServiceName(serviceName);
        }
        if (event.getInstanceId() == null) {
            event.setInstanceId(properties.getInstanceId());
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(Instant.now());
        }
        if (event.getHost() == null) {
            event.setHost(getHostname());
        }
        if (event.getEnvironment() == null) {
            event.setEnvironment(properties.getEnvironment());
        }
        if (event.getThread() == null) {
            event.setThread(Thread.currentThread().getName());
        }
    }

    private String getHostname() {
        if (hostname == null) {
            try {
                hostname = InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                hostname = "unknown";
            }
        }
        return hostname;
    }

    private String getStackTraceString(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append(element.toString()).append("\n");
            if (sb.length() > 2000) {
                sb.append("... truncated");
                break;
            }
        }
        return sb.toString();
    }
}
