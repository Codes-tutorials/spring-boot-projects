package org.codeart.cqrs.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.cqrs.query.ProductQueryHandler;
import org.codeart.cqrs.query.ProductView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Query Controller - Read API (CQRS Read Side).
 */
@RestController
@RequestMapping("/api/queries/products")
@RequiredArgsConstructor
@Tag(name = "Queries (Read)", description = "CQRS Read Side - Optimized query operations")
public class ProductQueryController {

    private final ProductQueryHandler queryHandler;

    @GetMapping
    @Operation(summary = "Get all products")
    public ResponseEntity<List<ProductView>> getAllProducts() {
        return ResponseEntity.ok(queryHandler.getAllProducts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductView> getProductById(@PathVariable String id) {
        return queryHandler.getProductById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "Get products by category")
    public ResponseEntity<List<ProductView>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(queryHandler.getProductsByCategory(category));
    }

    @GetMapping("/in-stock")
    @Operation(summary = "Get products in stock")
    public ResponseEntity<List<ProductView>> getInStock() {
        return ResponseEntity.ok(queryHandler.getProductsInStock());
    }

    @GetMapping("/stock-status/{status}")
    @Operation(summary = "Get products by stock status (IN_STOCK, LOW_STOCK, OUT_OF_STOCK)")
    public ResponseEntity<List<ProductView>> getByStockStatus(@PathVariable String status) {
        return ResponseEntity.ok(queryHandler.getProductsByStockStatus(status));
    }

    @GetMapping("/price-range")
    @Operation(summary = "Get products in price range")
    public ResponseEntity<List<ProductView>> getByPriceRange(
            @RequestParam double min,
            @RequestParam double max) {
        return ResponseEntity.ok(queryHandler.getProductsByPriceRange(min, max));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by name")
    public ResponseEntity<List<ProductView>> search(@RequestParam String q) {
        return ResponseEntity.ok(queryHandler.searchProducts(q));
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get dashboard summary (aggregated stats)")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(queryHandler.getDashboard());
    }

    @GetMapping("/stats/by-category")
    @Operation(summary = "Get product count by category")
    public ResponseEntity<Map<String, Long>> getCountByCategory() {
        return ResponseEntity.ok(queryHandler.getCountByCategory());
    }

    @GetMapping("/stats/by-stock-status")
    @Operation(summary = "Get product count by stock status")
    public ResponseEntity<Map<String, Long>> getCountByStockStatus() {
        return ResponseEntity.ok(queryHandler.getCountByStockStatus());
    }
}
