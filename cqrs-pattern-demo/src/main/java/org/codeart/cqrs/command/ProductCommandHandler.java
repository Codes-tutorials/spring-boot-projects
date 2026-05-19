package org.codeart.cqrs.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cqrs.aggregate.ProductAggregate;
import org.codeart.cqrs.event.DomainEvent;
import org.codeart.cqrs.event.EventStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command Handler - Processes write operations.
 * 
 * This is the WRITE SIDE of CQRS.
 * Handles commands, applies business logic, and stores events.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductCommandHandler {

    private final EventStore eventStore;

    // In-memory aggregate cache (in production, use a proper repository)
    private final Map<String, ProductAggregate> aggregates = new ConcurrentHashMap<>();

    /**
     * Handle CreateProduct command.
     */
    public String handle(ProductCommand.CreateProduct command) {
        log.info("COMMAND: CreateProduct name={}", command.name());

        ProductAggregate aggregate = new ProductAggregate();
        aggregate.create(
                command.name(),
                command.description(),
                command.price(),
                command.quantity(),
                command.category());

        // Store events
        List<DomainEvent> events = aggregate.getAndClearUncommittedEvents();
        events.forEach(eventStore::store);

        // Cache aggregate
        aggregates.put(aggregate.getId(), aggregate);

        log.info("COMMAND: Product created with id={}", aggregate.getId());
        return aggregate.getId();
    }

    /**
     * Handle UpdatePrice command.
     */
    public void handle(ProductCommand.UpdatePrice command) {
        log.info("COMMAND: UpdatePrice productId={}, newPrice={}",
                command.productId(), command.newPrice());

        ProductAggregate aggregate = loadAggregate(command.productId());
        aggregate.updatePrice(command.newPrice());

        List<DomainEvent> events = aggregate.getAndClearUncommittedEvents();
        events.forEach(eventStore::store);

        log.info("COMMAND: Price updated for product={}", command.productId());
    }

    /**
     * Handle UpdateQuantity command.
     */
    public void handle(ProductCommand.UpdateQuantity command) {
        log.info("COMMAND: UpdateQuantity productId={}, change={}",
                command.productId(), command.quantityChange());

        ProductAggregate aggregate = loadAggregate(command.productId());
        aggregate.updateQuantity(command.quantityChange(), command.reason());

        List<DomainEvent> events = aggregate.getAndClearUncommittedEvents();
        events.forEach(eventStore::store);

        log.info("COMMAND: Quantity updated for product={}", command.productId());
    }

    /**
     * Handle DeleteProduct command.
     */
    public void handle(ProductCommand.DeleteProduct command) {
        log.info("COMMAND: DeleteProduct productId={}", command.productId());

        ProductAggregate aggregate = loadAggregate(command.productId());
        aggregate.delete();

        List<DomainEvent> events = aggregate.getAndClearUncommittedEvents();
        events.forEach(eventStore::store);

        log.info("COMMAND: Product deleted id={}", command.productId());
    }

    /**
     * Load aggregate from cache or rebuild from events.
     */
    private ProductAggregate loadAggregate(String productId) {
        // Check cache first
        ProductAggregate cached = aggregates.get(productId);
        if (cached != null) {
            return cached;
        }

        // Rebuild from event store
        List<DomainEvent> events = eventStore.getEventsForAggregate(productId);
        if (events.isEmpty()) {
            throw new IllegalArgumentException("Product not found: " + productId);
        }

        ProductAggregate aggregate = ProductAggregate.fromEvents(events);
        aggregates.put(productId, aggregate);

        return aggregate;
    }
}
