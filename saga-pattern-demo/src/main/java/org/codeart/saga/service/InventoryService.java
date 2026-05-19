package org.codeart.saga.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated Inventory Service.
 * In real microservices, this would be a separate service.
 */
@Slf4j
@Service
public class InventoryService {

    private final Map<String, InventoryReservation> reservations = new ConcurrentHashMap<>();
    private final Map<String, Integer> inventory = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private volatile double failureRate = 0.0;

    public InventoryService() {
        // Initialize some inventory
        inventory.put("PROD-001", 100);
        inventory.put("PROD-002", 50);
        inventory.put("PROD-003", 10);
        inventory.put("PROD-004", 0); // Out of stock
    }

    /**
     * Reserve inventory for an order.
     * 
     * @return Reservation ID on success
     * @throws InventoryException on failure
     */
    public String reserveInventory(String orderId, String productId, int quantity) {
        log.info("INVENTORY: Reserving {} units of {} for order={}",
                quantity, productId, orderId);

        simulateLatency();

        // Simulate failure
        if (random.nextDouble() < failureRate) {
            log.error("INVENTORY: Reservation failed for order={} (simulated failure)", orderId);
            throw new InventoryException("Inventory service unavailable");
        }

        // Check inventory
        Integer available = inventory.getOrDefault(productId, 0);
        if (available < quantity) {
            log.error("INVENTORY: Insufficient stock for product={}, available={}, requested={}",
                    productId, available, quantity);
            throw new InventoryException("Insufficient inventory: only " + available + " available");
        }

        // Reserve inventory
        inventory.put(productId, available - quantity);
        String reservationId = "RES-" + System.currentTimeMillis();
        reservations.put(reservationId,
                new InventoryReservation(reservationId, orderId, productId, quantity, "RESERVED"));

        log.info("INVENTORY: Reserved successfully! reservationId={}", reservationId);
        return reservationId;
    }

    /**
     * Release reserved inventory (compensation/rollback).
     */
    public void releaseInventory(String reservationId) {
        log.info("INVENTORY COMPENSATION: Releasing reservation={}", reservationId);

        simulateLatency();

        InventoryReservation reservation = reservations.get(reservationId);
        if (reservation != null) {
            // Return items to inventory
            Integer current = inventory.getOrDefault(reservation.productId, 0);
            inventory.put(reservation.productId, current + reservation.quantity);
            // Replace with released status
            reservations.put(reservationId,
                    new InventoryReservation(reservation.id, reservation.orderId,
                            reservation.productId, reservation.quantity, "RELEASED"));
            log.info("INVENTORY COMPENSATION: Released {} units of {} back to inventory",
                    reservation.quantity, reservation.productId);
        } else {
            log.warn("INVENTORY COMPENSATION: Reservation {} not found", reservationId);
        }
    }

    public void setFailureRate(double rate) {
        this.failureRate = rate;
        log.info("Inventory failure rate set to {}%", rate * 100);
    }

    public double getFailureRate() {
        return failureRate;
    }

    public Map<String, Integer> getInventoryLevels() {
        return Map.copyOf(inventory);
    }

    private void simulateLatency() {
        try {
            Thread.sleep(100 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class InventoryReservation {
        String id, orderId, productId;
        int quantity;
        String status;

        public InventoryReservation(String id, String orderId, String productId, int quantity, String status) {
            this.id = id;
            this.orderId = orderId;
            this.productId = productId;
            this.quantity = quantity;
            this.status = status;
        }
    }

    public static class InventoryException extends RuntimeException {
        public InventoryException(String message) {
            super(message);
        }
    }
}
