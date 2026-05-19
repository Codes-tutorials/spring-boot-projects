package org.codeart.cache.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.Product;
import org.codeart.cache.pattern.CacheAsideService;
import org.codeart.cache.pattern.WriteBehindService;
import org.codeart.cache.pattern.WriteThroughService;
import org.codeart.cache.repository.ProductRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * REST controller demonstrating different caching patterns.
 */
@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Product API demonstrating caching patterns")
public class ProductController {

    private final CacheAsideService cacheAsideService;
    private final WriteThroughService writeThroughService;
    private final WriteBehindService writeBehindService;
    private final ProductRepository productRepository;

    // ========== Cache-Aside Pattern ==========

    @GetMapping("/cache-aside/{id}")
    @Operation(summary = "Get product by ID (Cache-Aside)", description = "Demonstrates lazy loading: check cache → load from DB on miss → cache result")
    public ResponseEntity<Product> getCacheAside(@PathVariable Long id) {
        return cacheAsideService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/cache-aside")
    @Operation(summary = "Create product (Cache-Aside)", description = "Create product - not cached until first read")
    public ResponseEntity<Product> createCacheAside(@RequestBody Product product) {
        return ResponseEntity.ok(cacheAsideService.create(product));
    }

    @PutMapping("/cache-aside/{id}")
    @Operation(summary = "Update product (Cache-Aside)", description = "Update DB then evict from cache - next read reloads fresh data")
    public ResponseEntity<Product> updateCacheAside(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        return ResponseEntity.ok(cacheAsideService.update(product));
    }

    @DeleteMapping("/cache-aside/{id}")
    @Operation(summary = "Delete product (Cache-Aside)")
    public ResponseEntity<Void> deleteCacheAside(@PathVariable Long id) {
        cacheAsideService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ========== Write-Through Pattern ==========

    @GetMapping("/write-through/{id}")
    @Operation(summary = "Get product by ID (Write-Through)", description = "Read from cache - always populated after writes")
    public ResponseEntity<Product> getWriteThrough(@PathVariable Long id) {
        return writeThroughService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/write-through")
    @Operation(summary = "Create product (Write-Through)", description = "Writes to cache AND database synchronously")
    public ResponseEntity<Product> createWriteThrough(@RequestBody Product product) {
        return ResponseEntity.ok(writeThroughService.create(product));
    }

    @PutMapping("/write-through/{id}")
    @Operation(summary = "Update product (Write-Through)", description = "Updates cache AND database synchronously")
    public ResponseEntity<Product> updateWriteThrough(@PathVariable Long id, @RequestBody Product product) {
        product.setId(id);
        return ResponseEntity.ok(writeThroughService.update(product));
    }

    // ========== Write-Behind Pattern ==========

    @GetMapping("/write-behind/{id}")
    @Operation(summary = "Get product by ID (Write-Behind)", description = "Read from cache, falls back to pending writes or DB")
    public ResponseEntity<Product> getWriteBehind(@PathVariable Long id) {
        return writeBehindService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/write-behind")
    @Operation(summary = "Create product (Write-Behind)", description = "Fast! Writes to cache immediately, queues async DB write")
    public ResponseEntity<Product> createWriteBehind(@RequestBody Product product) {
        return ResponseEntity.ok(writeBehindService.save(product));
    }

    @GetMapping("/write-behind/pending")
    @Operation(summary = "Get pending write count")
    public ResponseEntity<Map<String, Integer>> getPendingWrites() {
        return ResponseEntity.ok(Map.of("pending", writeBehindService.getPendingCount()));
    }

    @PostMapping("/write-behind/flush")
    @Operation(summary = "Force flush pending writes to database")
    public ResponseEntity<Map<String, String>> flushWriteBehind() {
        int flushes = writeBehindService.forceFlush();
        return ResponseEntity.ok(Map.of("status", "flushed", "batches", String.valueOf(flushes)));
    }

    // ========== Test Data ==========

    @PostMapping("/seed")
    @Operation(summary = "Seed test products")
    public ResponseEntity<List<Product>> seedProducts() {
        List<Product> products = List.of(
                Product.builder().sku("LAPTOP-001").name("Gaming Laptop").price(BigDecimal.valueOf(1299.99))
                        .quantity(50).category("Electronics").build(),
                Product.builder().sku("PHONE-001").name("Smartphone Pro").price(BigDecimal.valueOf(999.99))
                        .quantity(100).category("Electronics").build(),
                Product.builder().sku("HEADSET-001").name("Wireless Headset").price(BigDecimal.valueOf(149.99))
                        .quantity(200).category("Audio").build());
        return ResponseEntity.ok(productRepository.saveAll(products));
    }

    @GetMapping
    @Operation(summary = "Get all products (no cache)")
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(productRepository.findAll());
    }
}
