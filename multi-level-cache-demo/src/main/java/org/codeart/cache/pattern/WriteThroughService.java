package org.codeart.cache.pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.Product;
import org.codeart.cache.repository.ProductRepository;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Write-Through Pattern:
 * 
 * WRITE:
 * 1. Write to cache
 * 2. Synchronously write to database
 * 3. Return success only after both complete
 * 
 * READ:
 * 1. Read from cache (always populated after write)
 * 
 * Guarantees data consistency between cache and database.
 * Write latency is higher (cache + DB), but reads are always fast.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WriteThroughService {

    private final ProductRepository productRepository;

    public static final String CACHE_NAME = "products-wt";

    /**
     * Read from cache (populated by writes).
     */
    @Cacheable(value = CACHE_NAME, key = "#id")
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        log.info("WRITE-THROUGH: Cache miss for product ID: {}, loading from DB", id);
        return productRepository.findById(id);
    }

    /**
     * Write-Through: Update cache AND database synchronously.
     * 
     * @CachePut always executes the method and updates cache with result.
     */
    @CachePut(value = CACHE_NAME, key = "#result.id")
    @Transactional
    public Product save(Product product) {
        log.info("WRITE-THROUGH: Saving product {} to cache AND database synchronously",
                product.getSku());

        // Save to database
        Product saved = productRepository.save(product);

        // @CachePut will automatically update cache with returned value
        log.info("WRITE-THROUGH: Product {} saved to both cache and DB", saved.getId());

        return saved;
    }

    /**
     * Create with Write-Through.
     */
    @CachePut(value = CACHE_NAME, key = "#result.id")
    @Transactional
    public Product create(Product product) {
        log.info("WRITE-THROUGH: Creating product {} in cache AND database", product.getSku());
        return productRepository.save(product);
    }

    /**
     * Update with Write-Through.
     */
    @CachePut(value = CACHE_NAME, key = "#product.id")
    @Transactional
    public Product update(Product product) {
        log.info("WRITE-THROUGH: Updating product {} in cache AND database", product.getId());

        // Verify exists
        if (!productRepository.existsById(product.getId())) {
            throw new IllegalArgumentException("Product not found: " + product.getId());
        }

        return productRepository.save(product);
    }
}
