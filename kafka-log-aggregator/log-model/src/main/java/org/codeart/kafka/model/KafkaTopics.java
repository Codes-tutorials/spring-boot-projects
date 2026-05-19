package org.codeart.kafka.model;

/**
 * Constants for Kafka topics used in log aggregation.
 */
public final class KafkaTopics {

    private KafkaTopics() {}

    /**
     * Main topic for system logs
     */
    public static final String SYSTEM_LOGS = "system-logs";

    /**
     * Dead letter topic for failed log messages
     */
    public static final String SYSTEM_LOGS_DLT = "system-logs.DLT";

    /**
     * Topic for aggregated/processed logs
     */
    public static final String AGGREGATED_LOGS = "aggregated-logs";

    /**
     * Topic for error-level logs only
     */
    public static final String ERROR_LOGS = "error-logs";

    /**
     * Topic for audit logs
     */
    public static final String AUDIT_LOGS = "audit-logs";
}
