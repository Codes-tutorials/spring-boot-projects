package org.codeart.cqrs.query;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cqrs.event.DomainEvent;
import org.codeart.cqrs.event.EventStore;
import org.codeart.cqrs.event.ProductEvent.*;
import org.springframework.stereotype.Component;

/**
 * Projection Handler - Updates read model from events.
 * 
 * Subscribes to events from EventStore and updates the ProductView.
 * This keeps the read model synchronized with the write model.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductProjection {

    private final EventStore eventStore;
    private final ProductViewRepository viewRepository;

    @PostConstruct
    public void init() {
        // Subscribe to all events
        eventStore.subscribe(this::handleEvent);
        log.info("ProductProjection initialized and subscribed to events");
    }

    /**
     * Handle incoming events and update read model.
     */
    public void handleEvent(DomainEvent event) {
        log.debug("PROJECTION: Handling event {}", event.getEventType());

        if (event instanceof ProductCreated e) {
            handleProductCreated(e);
        } else if (event instanceof ProductPriceUpdated e) {
            handlePriceUpdated(e);
        } else if (event instanceof ProductQuantityUpdated e) {
            handleQuantityUpdated(e);
        } else if (event instanceof ProductDeleted e) {
            handleProductDeleted(e);
        } else {
            log.warn("Unhandled event type: {}", event.getEventType());
        }
    }

    private void handleProductCreated(ProductCreated event) {
        ProductView view = ProductView.builder()
                .id(event.aggregateId())
                .name(event.name())
                .description(event.description())
                .price(event.price())
                .quantity(event.quantity())
                .category(event.category())
                .deleted(false)
                .version(1)
                .build();

        viewRepository.save(view);
        log.info("PROJECTION: Created ProductView id={}", event.aggregateId());
    }

    private void handlePriceUpdated(ProductPriceUpdated event) {
        viewRepository.findById(event.aggregateId()).ifPresent(view -> {
            view.setPrice(event.newPrice());
            view.setVersion(view.getVersion() + 1);
            viewRepository.save(view);
            log.info("PROJECTION: Updated price for product={}", event.aggregateId());
        });
    }

    private void handleQuantityUpdated(ProductQuantityUpdated event) {
        viewRepository.findById(event.aggregateId()).ifPresent(view -> {
            view.setQuantity(event.newQuantity());
            view.setVersion(view.getVersion() + 1);
            viewRepository.save(view);
            log.info("PROJECTION: Updated quantity for product={}", event.aggregateId());
        });
    }

    private void handleProductDeleted(ProductDeleted event) {
        viewRepository.findById(event.aggregateId()).ifPresent(view -> {
            view.setDeleted(true);
            view.setVersion(view.getVersion() + 1);
            viewRepository.save(view);
            log.info("PROJECTION: Marked product as deleted id={}", event.aggregateId());
        });
    }
}
