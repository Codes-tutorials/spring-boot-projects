package org.codeart.saga.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Order entity - the main aggregate for SAGA.
 */
@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String customerId;
    private String productId;
    private int quantity;
    private double amount;

    @Enumerated(EnumType.STRING)
    private SagaStatus sagaStatus;

    private String paymentId;
    private String inventoryReservationId;
    private String shippingId;

    private String failureReason;

    @Column(updatable = false)
    private Instant createdAt;

    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "saga_steps", joinColumns = @JoinColumn(name = "order_id"))
    @OrderBy("timestamp ASC")
    private List<SagaStep> steps = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public void addStep(String stepName, String status, String details) {
        if (steps == null) {
            steps = new ArrayList<>();
        }
        steps.add(new SagaStep(stepName, status, details, Instant.now()));
    }
}
