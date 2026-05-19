package org.codeart.kafka.consumer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for log consumer.
 */
@Data
@Component
@ConfigurationProperties(prefix = "log-consumer")
public class LogConsumerProperties {

    /**
     * Unique instance identifier
     */
    private String instanceId;

    /**
     * Enable dead letter topic for failed messages
     */
    private boolean deadLetterEnabled = true;

    /**
     * Maximum retries before sending to DLT
     */
    private int maxRetries = 3;

    /**
     * Batch size for processing
     */
    private int batchSize = 50;

    /**
     * Persistence mode: console, file, elasticsearch
     */
    private String persistence = "console";
}
