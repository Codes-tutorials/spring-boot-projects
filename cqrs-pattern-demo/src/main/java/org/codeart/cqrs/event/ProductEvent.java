package org.codeart.cqrs.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Product-related domain events.
 */
public sealed interface ProductEvent extends DomainEvent {

    /**
     * Event: Product was created.
     */
    record ProductCreated(
            String eventId,
            String aggregateId,
            Instant timestamp,
            String name,
            String description,
            double price,
            int quantity,
            String category) implements ProductEvent {

        public ProductCreated(String aggregateId, String name, String description,
                double price, int quantity, String category) {
            this(UUID.randomUUID().toString(), aggregateId, Instant.now(),
                    name, description, price, quantity, category);
        }

        @Override
        public String getEventType() {
            return "PRODUCT_CREATED";
        }

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public String getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Event: Product price was updated.
     */
    record ProductPriceUpdated(
            String eventId,
            String aggregateId,
            Instant timestamp,
            double oldPrice,
            double newPrice) implements ProductEvent {

        public ProductPriceUpdated(String aggregateId, double oldPrice, double newPrice) {
            this(UUID.randomUUID().toString(), aggregateId, Instant.now(), oldPrice, newPrice);
        }

        @Override
        public String getEventType() {
            return "PRODUCT_PRICE_UPDATED";
        }

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public String getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Event: Product quantity was updated.
     */
    record ProductQuantityUpdated(
            String eventId,
            String aggregateId,
            Instant timestamp,
            int oldQuantity,
            int newQuantity,
            String reason) implements ProductEvent {

        public ProductQuantityUpdated(String aggregateId, int oldQuantity, int newQuantity, String reason) {
            this(UUID.randomUUID().toString(), aggregateId, Instant.now(), oldQuantity, newQuantity, reason);
        }

        @Override
        public String getEventType() {
            return "PRODUCT_QUANTITY_UPDATED";
        }

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public String getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Event: Product was deleted.
     */
    record ProductDeleted(
            String eventId,
            String aggregateId,
            Instant timestamp) implements ProductEvent {

        public ProductDeleted(String aggregateId) {
            this(UUID.randomUUID().toString(), aggregateId, Instant.now());
        }

        @Override
        public String getEventType() {
            return "PRODUCT_DELETED";
        }

        @Override
        public String getEventId() {
            return eventId;
        }

        @Override
        public String getAggregateId() {
            return aggregateId;
        }

        @Override
        public Instant getTimestamp() {
            return timestamp;
        }
    }
}
