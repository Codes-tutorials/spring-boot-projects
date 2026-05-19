package org.codeart.cache.pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.config.CacheProperties;
import org.codeart.cache.model.Product;
import org.codeart.cache.repository.ProductRepository;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Write-Behind (Write-Back) Pattern:
 * 
 * WRITE:
 * 1. Write to cache immediately
 * 2. Add to async write queue
 * 3. Return immediately (fast!)
 * 4. Background process flushes to database
 * 
 * READ:
 * 1. Read from cache
 * 
 * Benefits:
 * - Very fast writes
 * - Batch writes to database
 * - Reduced database load
 * 
 * Tradeoffs:
 * - Eventual consistency
 * - Risk of data loss if cache fails before flush
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WriteBehindService {

    private final ProductRepository productRepository;
    private final CacheManager cacheManager;
    private final CacheProperties cacheProperties;

    public static final String CACHE_NAME = "products-wb";

    // Write-behind queue for pending database writes
    private final ConcurrentLinkedQueue<Product> writeQueue = new ConcurrentLinkedQueue<>();

    // Track pending writes by ID (for deduplication)
    private final Map<Long, Product> pendingWrites = new ConcurrentHashMap<>();

    /**
     * Read from cache.
     */
    public Optional<Product> findById(Long id) {
        // Check cache first
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            Product cached = cache.get(id, Product.class);
            if (cached != null) {
                log.debug("WRITE-BEHIND: Cache HIT for product ID: {}", id);
                return Optional.of(cached);
            }
        }

        // Check pending writes
        Product pending = pendingWrites.get(id);
        if (pending != null) {
            log.debug("WRITE-BEHIND: Found in pending writes for ID: {}", id);
            return Optional.of(pending);
        }

        // Load from database
        log.info("WRITE-BEHIND: Cache miss for product ID: {}, loading from DB", id);
        Optional<Product> product = productRepository.findById(id);

        // Populate cache
        product.ifPresent(p -> {
            if (cache != null) {
                cache.put(id, p);
            }
        });

        return product;
    }

    /**
     * Write-Behind: Update cache immediately, queue async DB write.
     */
    public Product save(Product product) {
        log.info("WRITE-BEHIND: Saving product {} to cache, queueing async DB write",
                product.getSku());

        // If new product, assign temp ID for cache
        if (product.getId() == null) {
            product.setId(System.nanoTime()); // Temp ID until DB assigns real one
        }

        // Update cache immediately
        var cache = cacheManager.getCache(CACHE_NAME);
        if (cache != null) {
            cache.put(product.getId(), product);
        }

        // Add to write queue (deduplicate by ID)
        pendingWrites.put(product.getId(), product);
        writeQueue.offer(product);

        log.debug("WRITE-BEHIND: Product {} queued for async write. Queue size: {}",
                product.getId(), writeQueue.size());

        return product;
    }

    /**
     * Scheduled task to flush write queue to database.
     */
    @Scheduled(fixedRateString = "${cache.write-behind.flush-interval-ms:5000}")
    @Transactional
    public void flushWriteQueue() {
        if (writeQueue.isEmpty()) {
            return;
        }

        int batchSize = cacheProperties.getWriteBehind().getBatchSize();
        List<Product> batch = new ArrayList<>();

        Product product;
        while ((product = writeQueue.poll()) != null && batch.size() < batchSize) {
            batch.add(product);
            pendingWrites.remove(product.getId());
        }

        if (!batch.isEmpty()) {
            log.info("WRITE-BEHIND: Flushing {} products to database", batch.size());
            try {
                List<Product> saved = productRepository.saveAll(batch);

                // Update cache with real IDs
                var cache = cacheManager.getCache(CACHE_NAME);
                if (cache != null) {
                    saved.forEach(p -> cache.put(p.getId(), p));
                }

                log.info("WRITE-BEHIND: Successfully flushed {} products", saved.size());
            } catch (Exception e) {
                log.error("WRITE-BEHIND: Failed to flush batch to database", e);
                // Re-queue failed items
                writeQueue.addAll(batch);
                batch.forEach(p -> pendingWrites.put(p.getId(), p));
            }
        }
    }

    /**
     * Force flush all pending writes (for shutdown, etc.)
     */
    @Transactional
    public int forceFlush() {
        int count = 0;
        while (!writeQueue.isEmpty()) {
            flushWriteQueue();
            count++;
        }
        return count;
    }

    /**
     * Get pending write queue size.
     */
    public int getPendingCount() {
        return writeQueue.size();
    }
}
