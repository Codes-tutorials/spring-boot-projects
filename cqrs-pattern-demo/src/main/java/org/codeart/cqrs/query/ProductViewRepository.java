package org.codeart.cqrs.query;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Read-only repository for product queries.
 */
@Repository
public interface ProductViewRepository extends JpaRepository<ProductView, String> {

    List<ProductView> findByDeletedFalse();

    List<ProductView> findByCategory(String category);

    List<ProductView> findByCategoryAndDeletedFalse(String category);

    List<ProductView> findByInStockTrueAndDeletedFalse();

    List<ProductView> findByStockStatus(String stockStatus);

    @Query("SELECT p FROM ProductView p WHERE p.price BETWEEN :minPrice AND :maxPrice AND p.deleted = false")
    List<ProductView> findByPriceRange(double minPrice, double maxPrice);

    @Query("SELECT p FROM ProductView p WHERE LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) AND p.deleted = false")
    List<ProductView> searchByName(String search);

    @Query("SELECT p.category, COUNT(p) FROM ProductView p WHERE p.deleted = false GROUP BY p.category")
    List<Object[]> countByCategory();

    @Query("SELECT p.stockStatus, COUNT(p) FROM ProductView p WHERE p.deleted = false GROUP BY p.stockStatus")
    List<Object[]> countByStockStatus();
}
