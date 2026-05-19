package org.codeart.cqrs.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Base event interface for event sourcing.
 */
public interface DomainEvent {
    String getEventId();

    String getAggregateId();

    Instant getTimestamp();

    String getEventType();
}
