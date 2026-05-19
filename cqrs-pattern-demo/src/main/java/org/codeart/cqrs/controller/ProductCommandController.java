package org.codeart.cqrs.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.codeart.cqrs.command.ProductCommand;
import org.codeart.cqrs.command.ProductCommand.*;
import org.codeart.cqrs.command.ProductCommandHandler;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;

/**
 * Command Controller - Write API (CQRS Write Side).
 */
@RestController
@RequestMapping("/api/commands/products")
@RequiredArgsConstructor
@Tag(name = "Commands (Write)", description = "CQRS Write Side - Create, Update, Delete operations")
public class ProductCommandController {

    private final ProductCommandHandler commandHandler;

    @PostMapping
    @Operation(summary = "Create product", description = "Command: CreateProduct")
    public ResponseEntity<Map<String, String>> createProduct(@Valid @RequestBody CreateProduct command) {
        String id = commandHandler.handle(command);
        return ResponseEntity
                .created(URI.create("/api/queries/products/" + id))
                .body(Map.of("id", id, "message", "Product created"));
    }

    @PutMapping("/{id}/price")
    @Operation(summary = "Update price", description = "Command: UpdatePrice")
    public ResponseEntity<Map<String, String>> updatePrice(
            @PathVariable String id,
            @RequestParam double newPrice) {
        commandHandler.handle(new UpdatePrice(id, newPrice));
        return ResponseEntity.ok(Map.of("message", "Price updated"));
    }

    @PutMapping("/{id}/quantity")
    @Operation(summary = "Update quantity", description = "Command: UpdateQuantity")
    public ResponseEntity<Map<String, String>> updateQuantity(
            @PathVariable String id,
            @RequestParam int change,
            @RequestParam(defaultValue = "Manual adjustment") String reason) {
        commandHandler.handle(new UpdateQuantity(id, change, reason));
        return ResponseEntity.ok(Map.of("message", "Quantity updated"));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete product", description = "Command: DeleteProduct")
    public ResponseEntity<Map<String, String>> deleteProduct(@PathVariable String id) {
        commandHandler.handle(new DeleteProduct(id));
        return ResponseEntity.ok(Map.of("message", "Product deleted"));
    }
}
