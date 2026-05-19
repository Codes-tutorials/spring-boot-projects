package org.codeart.cqrs.query;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * Read Model - Optimized for queries.
 * 
 * This is the QUERY SIDE of CQRS.
 * Denormalized, optimized for fast reads.
 * Updated by projecting events from the write side.
 */
@Entity
@Table(name = "product_view")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductView {

    @Id
    private String id;

    private String name;
    private String description;
    private double price;
    private int quantity;
    private String category;

    private boolean inStock;
    private String stockStatus; // IN_STOCK, LOW_STOCK, OUT_OF_STOCK
    private boolean deleted;

    private Instant createdAt;
    private Instant updatedAt;
    private int version; // For optimistic locking

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        updateStockStatus();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
        updateStockStatus();
    }

    private void updateStockStatus() {
        if (quantity <= 0) {
            this.inStock = false;
            this.stockStatus = "OUT_OF_STOCK";
        } else if (quantity <= 10) {
            this.inStock = true;
            this.stockStatus = "LOW_STOCK";
        } else {
            this.inStock = true;
            this.stockStatus = "IN_STOCK";
        }
    }
}
