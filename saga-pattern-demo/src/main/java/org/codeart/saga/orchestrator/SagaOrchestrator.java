package org.codeart.saga.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.saga.model.Order;
import org.codeart.saga.model.OrderRepository;
import org.codeart.saga.model.SagaStatus;
import org.codeart.saga.service.InventoryService;
import org.codeart.saga.service.PaymentService;
import org.codeart.saga.service.ShippingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SAGA Orchestrator - Coordinates the distributed transaction.
 * 
 * Order Flow:
 * 1. Create Order -> STARTED
 * 2. Process Payment -> PAYMENT_COMPLETED or PAYMENT_FAILED (compensate)
 * 3. Reserve Inventory -> INVENTORY_RESERVED or INVENTORY_FAILED (compensate)
 * 4. Schedule Shipping -> SHIPPING_SCHEDULED or SHIPPING_FAILED (compensate)
 * 5. Complete -> COMPLETED
 * 
 * Compensation (Rollback):
 * - If Shipping fails: Release Inventory -> Refund Payment
 * - If Inventory fails: Refund Payment
 * - If Payment fails: No compensation needed (first step)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final ShippingService shippingService;

    /**
     * Execute the complete SAGA for an order.
     */
    @Transactional
    public Order executeSaga(Order order) {
        log.info("=== SAGA STARTED for order={} ===", order.getId());
        order.setSagaStatus(SagaStatus.STARTED);
        order.addStep("SAGA_START", "SUCCESS", "SAGA execution started");
        orderRepository.save(order);

        try {
            // Step 1: Process Payment
            order = processPaymentStep(order);
            if (order.getSagaStatus() == SagaStatus.PAYMENT_FAILED) {
                return order;
            }

            // Step 2: Reserve Inventory
            order = reserveInventoryStep(order);
            if (order.getSagaStatus() == SagaStatus.INVENTORY_FAILED) {
                compensatePayment(order);
                return order;
            }

            // Step 3: Schedule Shipping
            order = scheduleShippingStep(order);
            if (order.getSagaStatus() == SagaStatus.SHIPPING_FAILED) {
                compensateInventory(order);
                compensatePayment(order);
                return order;
            }

            // All steps succeeded
            order.setSagaStatus(SagaStatus.COMPLETED);
            order.addStep("SAGA_COMPLETE", "SUCCESS", "All saga steps completed successfully");
            log.info("=== SAGA COMPLETED for order={} ===", order.getId());

        } catch (Exception e) {
            log.error("SAGA FAILED with unexpected error: {}", e.getMessage(), e);
            order.setSagaStatus(SagaStatus.FAILED);
            order.setFailureReason("Unexpected error: " + e.getMessage());
            order.addStep("SAGA_ERROR", "FAILED", e.getMessage());

            // Attempt full compensation
            compensateSaga(order);
        }

        return orderRepository.save(order);
    }

    /**
     * Step 1: Process Payment
     */
    private Order processPaymentStep(Order order) {
        log.info("SAGA Step 1: Processing payment for order={}", order.getId());
        order.setSagaStatus(SagaStatus.PAYMENT_PENDING);
        order.addStep("PAYMENT", "PENDING", "Processing payment of $" + order.getAmount());
        orderRepository.save(order);

        try {
            String paymentId = paymentService.processPayment(
                    order.getId(), order.getCustomerId(), order.getAmount());

            order.setPaymentId(paymentId);
            order.setSagaStatus(SagaStatus.PAYMENT_COMPLETED);
            order.addStep("PAYMENT", "SUCCESS", "Payment processed: " + paymentId);
            log.info("SAGA Step 1: Payment SUCCESS for order={}", order.getId());

        } catch (PaymentService.PaymentException e) {
            order.setSagaStatus(SagaStatus.PAYMENT_FAILED);
            order.setFailureReason("Payment failed: " + e.getMessage());
            order.addStep("PAYMENT", "FAILED", e.getMessage());
            log.error("SAGA Step 1: Payment FAILED for order={}: {}", order.getId(), e.getMessage());
        }

        return orderRepository.save(order);
    }

    /**
     * Step 2: Reserve Inventory
     */
    private Order reserveInventoryStep(Order order) {
        log.info("SAGA Step 2: Reserving inventory for order={}", order.getId());
        order.setSagaStatus(SagaStatus.INVENTORY_PENDING);
        order.addStep("INVENTORY", "PENDING",
                "Reserving " + order.getQuantity() + " units of " + order.getProductId());
        orderRepository.save(order);

        try {
            String reservationId = inventoryService.reserveInventory(
                    order.getId(), order.getProductId(), order.getQuantity());

            order.setInventoryReservationId(reservationId);
            order.setSagaStatus(SagaStatus.INVENTORY_RESERVED);
            order.addStep("INVENTORY", "SUCCESS", "Inventory reserved: " + reservationId);
            log.info("SAGA Step 2: Inventory SUCCESS for order={}", order.getId());

        } catch (InventoryService.InventoryException e) {
            order.setSagaStatus(SagaStatus.INVENTORY_FAILED);
            order.setFailureReason("Inventory failed: " + e.getMessage());
            order.addStep("INVENTORY", "FAILED", e.getMessage());
            log.error("SAGA Step 2: Inventory FAILED for order={}: {}", order.getId(), e.getMessage());
        }

        return orderRepository.save(order);
    }

    /**
     * Step 3: Schedule Shipping
     */
    private Order scheduleShippingStep(Order order) {
        log.info("SAGA Step 3: Scheduling shipping for order={}", order.getId());
        order.setSagaStatus(SagaStatus.SHIPPING_PENDING);
        order.addStep("SHIPPING", "PENDING", "Scheduling shipment");
        orderRepository.save(order);

        try {
            String shippingId = shippingService.scheduleShipping(
                    order.getId(), order.getCustomerId(), order.getProductId(), order.getQuantity());

            order.setShippingId(shippingId);
            order.setSagaStatus(SagaStatus.SHIPPING_SCHEDULED);
            order.addStep("SHIPPING", "SUCCESS", "Shipping scheduled: " + shippingId);
            log.info("SAGA Step 3: Shipping SUCCESS for order={}", order.getId());

        } catch (ShippingService.ShippingException e) {
            order.setSagaStatus(SagaStatus.SHIPPING_FAILED);
            order.setFailureReason("Shipping failed: " + e.getMessage());
            order.addStep("SHIPPING", "FAILED", e.getMessage());
            log.error("SAGA Step 3: Shipping FAILED for order={}: {}", order.getId(), e.getMessage());
        }

        return orderRepository.save(order);
    }

    /**
     * Compensate (rollback) payment step.
     */
    private void compensatePayment(Order order) {
        if (order.getPaymentId() != null) {
            log.info("COMPENSATION: Refunding payment for order={}", order.getId());
            try {
                paymentService.refundPayment(order.getPaymentId());
                order.addStep("PAYMENT_COMPENSATION", "SUCCESS",
                        "Payment refunded: " + order.getPaymentId());
            } catch (Exception e) {
                log.error("COMPENSATION FAILED: Could not refund payment {}", order.getPaymentId(), e);
                order.addStep("PAYMENT_COMPENSATION", "FAILED", e.getMessage());
            }
        }
    }

    /**
     * Compensate (rollback) inventory step.
     */
    private void compensateInventory(Order order) {
        if (order.getInventoryReservationId() != null) {
            log.info("COMPENSATION: Releasing inventory for order={}", order.getId());
            try {
                inventoryService.releaseInventory(order.getInventoryReservationId());
                order.addStep("INVENTORY_COMPENSATION", "SUCCESS",
                        "Inventory released: " + order.getInventoryReservationId());
            } catch (Exception e) {
                log.error("COMPENSATION FAILED: Could not release inventory {}",
                        order.getInventoryReservationId(), e);
                order.addStep("INVENTORY_COMPENSATION", "FAILED", e.getMessage());
            }
        }
    }

    /**
     * Compensate (rollback) shipping step.
     */
    private void compensateShipping(Order order) {
        if (order.getShippingId() != null) {
            log.info("COMPENSATION: Cancelling shipping for order={}", order.getId());
            try {
                shippingService.cancelShipping(order.getShippingId());
                order.addStep("SHIPPING_COMPENSATION", "SUCCESS",
                        "Shipping cancelled: " + order.getShippingId());
            } catch (Exception e) {
                log.error("COMPENSATION FAILED: Could not cancel shipping {}",
                        order.getShippingId(), e);
                order.addStep("SHIPPING_COMPENSATION", "FAILED", e.getMessage());
            }
        }
    }

    /**
     * Full compensation for all completed steps.
     */
    private void compensateSaga(Order order) {
        log.info("=== SAGA COMPENSATION STARTED for order={} ===", order.getId());
        order.setSagaStatus(SagaStatus.COMPENSATING);
        order.addStep("SAGA_COMPENSATION", "STARTED", "Rolling back all completed steps");

        compensateShipping(order);
        compensateInventory(order);
        compensatePayment(order);

        order.setSagaStatus(SagaStatus.COMPENSATED);
        order.addStep("SAGA_COMPENSATION", "COMPLETED", "All steps rolled back");
        log.info("=== SAGA COMPENSATION COMPLETED for order={} ===", order.getId());
    }
}
