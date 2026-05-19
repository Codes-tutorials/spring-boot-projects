package org.codeart.cqrs.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * In-memory Event Store for event sourcing.
 * In production, use a database like PostgreSQL, EventStore, or Kafka.
 */
@Slf4j
@Component
public class EventStore {

    // All events stored in order
    private final List<DomainEvent> events = new CopyOnWriteArrayList<>();

    // Events indexed by aggregate ID
    private final Map<String, List<DomainEvent>> eventsByAggregate = new ConcurrentHashMap<>();

    // Event handlers (subscribers)
    private final List<Consumer<DomainEvent>> handlers = new CopyOnWriteArrayList<>();

    /**
     * Store an event and notify handlers.
     */
    public void store(DomainEvent event) {
        events.add(event);
        eventsByAggregate.computeIfAbsent(event.getAggregateId(), k -> new CopyOnWriteArrayList<>())
                .add(event);

        log.debug("EVENT STORED: {} for aggregate={}", event.getEventType(), event.getAggregateId());

        // Notify all handlers (for read model sync)
        handlers.forEach(handler -> {
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.error("Event handler failed for event: {}", event.getEventType(), e);
            }
        });
    }

    /**
     * Get all events for an aggregate (for event replay).
     */
    public List<DomainEvent> getEventsForAggregate(String aggregateId) {
        return new ArrayList<>(eventsByAggregate.getOrDefault(aggregateId, List.of()));
    }

    /**
     * Get all events (for debugging/admin).
     */
    public List<DomainEvent> getAllEvents() {
        return new ArrayList<>(events);
    }

    /**
     * Get events by type.
     */
    public List<DomainEvent> getEventsByType(String eventType) {
        return events.stream()
                .filter(e -> e.getEventType().equals(eventType))
                .collect(Collectors.toList());
    }

    /**
     * Subscribe to events (for read model projections).
     */
    public void subscribe(Consumer<DomainEvent> handler) {
        handlers.add(handler);
        log.info("Event handler subscribed. Total handlers: {}", handlers.size());
    }

    /**
     * Get event count.
     */
    public int getEventCount() {
        return events.size();
    }

    /**
     * Clear all events (for testing).
     */
    public void clear() {
        events.clear();
        eventsByAggregate.clear();
        log.info("Event store cleared");
    }
}
