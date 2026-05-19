package org.codeart.cqrs.aggregate;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cqrs.event.DomainEvent;
import org.codeart.cqrs.event.ProductEvent;
import org.codeart.cqrs.event.ProductEvent.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Product Aggregate - Write Model.
 * 
 * The aggregate is the consistency boundary in CQRS/ES.
 * All business rules are enforced here.
 * State is rebuilt from events (event sourcing).
 */
@Slf4j
@Getter
public class ProductAggregate {

    private String id;
    private String name;
    private String description;
    private double price;
    private int quantity;
    private String category;
    private boolean deleted;

    // Uncommitted events (to be stored)
    private final List<DomainEvent> uncommittedEvents = new ArrayList<>();

    /**
     * Create new aggregate (for new products).
     */
    public ProductAggregate() {
        this.id = UUID.randomUUID().toString();
    }

    /**
     * Recreate aggregate from event history.
     */
    public static ProductAggregate fromEvents(List<DomainEvent> events) {
        ProductAggregate aggregate = new ProductAggregate();
        events.forEach(aggregate::apply);
        return aggregate;
    }

    /**
     * Command: Create product.
     */
    public void create(String name, String description, double price, int quantity, String category) {
        if (this.name != null) {
            throw new IllegalStateException("Product already exists");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (quantity < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }

        // Emit event
        ProductCreated event = new ProductCreated(this.id, name, description, price, quantity, category);
        apply(event);
        uncommittedEvents.add(event);
    }

    /**
     * Command: Update price.
     */
    public void updatePrice(double newPrice) {
        validateNotDeleted();
        if (newPrice <= 0) {
            throw new IllegalArgumentException("Price must be positive");
        }
        if (newPrice == this.price) {
            return; // No change
        }

        ProductPriceUpdated event = new ProductPriceUpdated(this.id, this.price, newPrice);
        apply(event);
        uncommittedEvents.add(event);
    }

    /**
     * Command: Update quantity.
     */
    public void updateQuantity(int change, String reason) {
        validateNotDeleted();
        int newQuantity = this.quantity + change;
        if (newQuantity < 0) {
            throw new IllegalArgumentException("Insufficient quantity. Available: " + this.quantity);
        }

        ProductQuantityUpdated event = new ProductQuantityUpdated(this.id, this.quantity, newQuantity, reason);
        apply(event);
        uncommittedEvents.add(event);
    }

    /**
     * Command: Delete product.
     */
    public void delete() {
        validateNotDeleted();

        ProductDeleted event = new ProductDeleted(this.id);
        apply(event);
        uncommittedEvents.add(event);
    }

    /**
     * Apply event to update aggregate state.
     */
    private void apply(DomainEvent event) {
        if (event instanceof ProductCreated e) {
            this.id = e.aggregateId();
            this.name = e.name();
            this.description = e.description();
            this.price = e.price();
            this.quantity = e.quantity();
            this.category = e.category();
            this.deleted = false;
        } else if (event instanceof ProductPriceUpdated e) {
            this.price = e.newPrice();
        } else if (event instanceof ProductQuantityUpdated e) {
            this.quantity = e.newQuantity();
        } else if (event instanceof ProductDeleted) {
            this.deleted = true;
        }
    }

    /**
     * Get and clear uncommitted events.
     */
    public List<DomainEvent> getAndClearUncommittedEvents() {
        List<DomainEvent> events = new ArrayList<>(uncommittedEvents);
        uncommittedEvents.clear();
        return events;
    }

    private void validateNotDeleted() {
        if (deleted) {
            throw new IllegalStateException("Product has been deleted");
        }
    }
}
