package org.codeart.cqrs.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.cqrs.command.ProductCommand.*;
import org.codeart.cqrs.command.ProductCommandHandler;
import org.codeart.cqrs.event.EventStore;
import org.codeart.cqrs.query.ProductQueryHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Demo Controller - Demonstrates CQRS pattern.
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Tag(name = "Demo", description = "Demonstrate CQRS pattern with samples")
public class DemoController {

    private final ProductCommandHandler commandHandler;
    private final ProductQueryHandler queryHandler;
    private final EventStore eventStore;

    @GetMapping("/seed")
    @Operation(summary = "Seed sample data", description = "Creates sample products")
    public ResponseEntity<Map<String, Object>> seedData() {
        // Create sample products via commands
        String id1 = commandHandler.handle(new CreateProduct(
                "iPhone 15 Pro", "Latest Apple smartphone", 999.99, 50, "Electronics"));
        String id2 = commandHandler.handle(new CreateProduct(
                "MacBook Pro 14\"", "Apple laptop with M3 chip", 1999.99, 25, "Electronics"));
        String id3 = commandHandler.handle(new CreateProduct(
                "AirPods Pro", "Wireless earbuds", 249.99, 100, "Accessories"));
        String id4 = commandHandler.handle(new CreateProduct(
                "Magic Keyboard", "Wireless keyboard", 99.99, 5, "Accessories"));
        String id5 = commandHandler.handle(new CreateProduct(
                "Studio Display", "27-inch 5K display", 1599.99, 0, "Displays"));

        return ResponseEntity.ok(Map.of(
                "message", "Sample data created",
                "products", List.of(id1, id2, id3, id4, id5),
                "totalEvents", eventStore.getEventCount()));
    }

    @GetMapping("/cqrs-demo")
    @Operation(summary = "Full CQRS demo", description = "Shows write/read separation")
    public ResponseEntity<Map<String, Object>> cqrsDemo() {
        // COMMAND: Create product (write side)
        String productId = commandHandler.handle(new CreateProduct(
                "Demo Product", "Created via CQRS command", 49.99, 10, "Demo"));

        // QUERY: Read the product (read side)
        var product = queryHandler.getProductById(productId);

        // COMMAND: Update price
        commandHandler.handle(new UpdatePrice(productId, 59.99));

        // QUERY: Read again to see updated price
        var updatedProduct = queryHandler.getProductById(productId);

        // COMMAND: Update quantity
        commandHandler.handle(new UpdateQuantity(productId, -5, "Sold 5 units"));

        // QUERY: Final state
        var finalProduct = queryHandler.getProductById(productId);

        return ResponseEntity.ok(Map.of(
                "explanation", "This demo shows CQRS in action:",
                "steps", List.of(
                        "1. COMMAND: CreateProduct -> Event stored -> Read model updated",
                        "2. QUERY: Get product -> Returns from optimized read model",
                        "3. COMMAND: UpdatePrice -> Event stored -> Read model synced",
                        "4. QUERY: Get product -> Shows new price",
                        "5. COMMAND: UpdateQuantity -> Event stored -> Stock status updated",
                        "6. QUERY: Get product -> Shows final state with stock status"),
                "productId", productId,
                "afterCreate", product.orElse(null),
                "afterPriceUpdate", updatedProduct.orElse(null),
                "afterQuantityUpdate", finalProduct.orElse(null),
                "totalEvents", eventStore.getEventCount()));
    }

    @GetMapping("/events")
    @Operation(summary = "View all events in event store")
    public ResponseEntity<Map<String, Object>> getEvents() {
        return ResponseEntity.ok(Map.of(
                "totalEvents", eventStore.getEventCount(),
                "events", eventStore.getAllEvents().stream()
                        .map(e -> Map.of(
                                "eventId", e.getEventId(),
                                "type", e.getEventType(),
                                "aggregateId", e.getAggregateId(),
                                "timestamp", e.getTimestamp().toString()))
                        .toList()));
    }

    @GetMapping("/events/{aggregateId}")
    @Operation(summary = "View events for a specific aggregate")
    public ResponseEntity<Map<String, Object>> getEventsForAggregate(@PathVariable String aggregateId) {
        var events = eventStore.getEventsForAggregate(aggregateId);
        return ResponseEntity.ok(Map.of(
                "aggregateId", aggregateId,
                "eventCount", events.size(),
                "events", events));
    }
}
