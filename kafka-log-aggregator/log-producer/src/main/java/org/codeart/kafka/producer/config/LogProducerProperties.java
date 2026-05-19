package org.codeart.kafka.producer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for log producer.
 */
@Data
@Component
@ConfigurationProperties(prefix = "log-producer")
public class LogProducerProperties {

    /**
     * Unique instance identifier for multi-instance deployments
     */
    private String instanceId;

    /**
     * Environment name (dev, staging, prod)
     */
    private String environment = "dev";

    /**
     * Enable asynchronous sending
     */
    private boolean asyncSend = true;

    /**
     * Topic configuration
     */
    private Map<String, String> topics = new HashMap<>();

    /**
     * Minimum log level to send (TRACE, DEBUG, INFO, WARN, ERROR)
     */
    private String minLevel = "INFO";

    /**
     * Get the system logs topic
     */
    public String getSystemLogsTopic() {
        return topics.getOrDefault("system-logs", "system-logs");
    }

    /**
     * Get the error logs topic
     */
    public String getErrorLogsTopic() {
        return topics.getOrDefault("error-logs", "error-logs");
    }
}
