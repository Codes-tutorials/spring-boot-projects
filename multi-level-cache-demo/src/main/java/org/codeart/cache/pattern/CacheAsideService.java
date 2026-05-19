package org.codeart.cache.pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.Product;
import org.codeart.cache.repository.ProductRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Cache-Aside Pattern (Lazy Loading):
 * 
 * READ:
 * 1. Check cache for data
 * 2. If cache miss, load from database
 * 3. Store result in cache
 * 4. Return data
 * 
 * WRITE:
 * 1. Update database
 * 2. Invalidate cache (delete, not update)
 * 
 * This is the most common caching pattern. The application manages the cache
 * explicitly. Cache is populated lazily on first read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheAsideService {

    private final ProductRepository productRepository;

    public static final String CACHE_NAME = "products";

    /**
     * Read with Cache-Aside pattern.
     * 
     * @Cacheable checks cache first, calls method on miss, then caches result.
     */
    @Cacheable(value = CACHE_NAME, key = "#id")
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        log.info("CACHE-ASIDE: Cache miss for product ID: {}, loading from DB", id);
        simulateSlowDbQuery();
        return productRepository.findById(id);
    }

    /**
     * Read by SKU with caching.
     */
    @Cacheable(value = CACHE_NAME, key = "'sku:' + #sku")
    @Transactional(readOnly = true)
    public Optional<Product> findBySku(String sku) {
        log.info("CACHE-ASIDE: Cache miss for SKU: {}, loading from DB", sku);
        simulateSlowDbQuery();
        return productRepository.findBySku(sku);
    }

    /**
     * Create product - no caching on create, will be cached on first read.
     */
    @Transactional
    public Product create(Product product) {
        log.info("CACHE-ASIDE: Creating product: {}", product.getSku());
        return productRepository.save(product);
    }

    /**
     * Update with Cache-Aside pattern.
     * After update, evict from cache (don't update cache).
     * Next read will reload fresh data.
     */
    @CacheEvict(value = CACHE_NAME, key = "#product.id")
    @Transactional
    public Product update(Product product) {
        log.info("CACHE-ASIDE: Updating product ID: {}, evicting from cache", product.getId());
        return productRepository.save(product);
    }

    /**
     * Delete with cache eviction.
     */
    @CacheEvict(value = CACHE_NAME, key = "#id")
    @Transactional
    public void delete(Long id) {
        log.info("CACHE-ASIDE: Deleting product ID: {}, evicting from cache", id);
        productRepository.deleteById(id);
    }

    /**
     * Evict specific entry from cache manually.
     */
    @CacheEvict(value = CACHE_NAME, key = "#id")
    public void evict(Long id) {
        log.info("CACHE-ASIDE: Manually evicting product ID: {} from cache", id);
    }

    /**
     * Evict all entries from cache.
     */
    @CacheEvict(value = CACHE_NAME, allEntries = true)
    public void evictAll() {
        log.info("CACHE-ASIDE: Evicting all entries from cache");
    }

    private void simulateSlowDbQuery() {
        try {
            Thread.sleep(100); // Simulate slow DB
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
