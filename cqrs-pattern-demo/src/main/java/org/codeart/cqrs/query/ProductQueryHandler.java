package org.codeart.cqrs.query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Query Handler - Processes read operations.
 * 
 * This is the READ SIDE of CQRS.
 * Optimized for fast queries, no business logic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductQueryHandler {

    private final ProductViewRepository viewRepository;

    /**
     * Get all active products.
     */
    public List<ProductView> getAllProducts() {
        log.debug("QUERY: Get all products");
        return viewRepository.findByDeletedFalse();
    }

    /**
     * Get product by ID.
     */
    public Optional<ProductView> getProductById(String id) {
        log.debug("QUERY: Get product by id={}", id);
        return viewRepository.findById(id)
                .filter(p -> !p.isDeleted());
    }

    /**
     * Get products by category.
     */
    public List<ProductView> getProductsByCategory(String category) {
        log.debug("QUERY: Get products by category={}", category);
        return viewRepository.findByCategoryAndDeletedFalse(category);
    }

    /**
     * Get products in stock.
     */
    public List<ProductView> getProductsInStock() {
        log.debug("QUERY: Get products in stock");
        return viewRepository.findByInStockTrueAndDeletedFalse();
    }

    /**
     * Get products by stock status.
     */
    public List<ProductView> getProductsByStockStatus(String stockStatus) {
        log.debug("QUERY: Get products by stockStatus={}", stockStatus);
        return viewRepository.findByStockStatus(stockStatus);
    }

    /**
     * Get products in price range.
     */
    public List<ProductView> getProductsByPriceRange(double minPrice, double maxPrice) {
        log.debug("QUERY: Get products by price range {}-{}", minPrice, maxPrice);
        return viewRepository.findByPriceRange(minPrice, maxPrice);
    }

    /**
     * Search products by name.
     */
    public List<ProductView> searchProducts(String search) {
        log.debug("QUERY: Search products by name={}", search);
        return viewRepository.searchByName(search);
    }

    /**
     * Get product count by category.
     */
    public Map<String, Long> getCountByCategory() {
        log.debug("QUERY: Count by category");
        return viewRepository.countByCategory().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]));
    }

    /**
     * Get product count by stock status.
     */
    public Map<String, Long> getCountByStockStatus() {
        log.debug("QUERY: Count by stock status");
        return viewRepository.countByStockStatus().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]));
    }

    /**
     * Get dashboard summary.
     */
    public Map<String, Object> getDashboard() {
        List<ProductView> all = viewRepository.findByDeletedFalse();
        return Map.of(
                "totalProducts", all.size(),
                "totalValue", all.stream().mapToDouble(p -> p.getPrice() * p.getQuantity()).sum(),
                "inStock", all.stream().filter(ProductView::isInStock).count(),
                "outOfStock", all.stream().filter(p -> !p.isInStock()).count(),
                "lowStock", all.stream().filter(p -> "LOW_STOCK".equals(p.getStockStatus())).count(),
                "byCategory", getCountByCategory(),
                "byStockStatus", getCountByStockStatus());
    }
}
