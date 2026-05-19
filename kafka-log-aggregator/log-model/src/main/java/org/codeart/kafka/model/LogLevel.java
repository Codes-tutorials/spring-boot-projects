package org.codeart.kafka.model;

/**
 * Log level constants matching standard logging frameworks.
 */
public enum LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL;

    public static LogLevel fromString(String level) {
        if (level == null) {
            return INFO;
        }
        try {
            return valueOf(level.toUpperCase());
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }

    public boolean isHigherOrEqual(LogLevel other) {
        return this.ordinal() >= other.ordinal();
    }
}
