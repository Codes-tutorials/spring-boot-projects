package org.codeart.saga.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulated Shipping Service.
 * In real microservices, this would be a separate service.
 */
@Slf4j
@Service
public class ShippingService {

    private final Map<String, ShippingRecord> shipments = new ConcurrentHashMap<>();
    private final Random random = new Random();

    private volatile double failureRate = 0.0;

    /**
     * Schedule shipping for an order.
     * 
     * @return Shipping ID on success
     * @throws ShippingException on failure
     */
    public String scheduleShipping(String orderId, String customerId, String productId, int quantity) {
        log.info("SHIPPING: Scheduling shipment for order={}, customer={}, product={}, qty={}",
                orderId, customerId, productId, quantity);

        simulateLatency();

        // Simulate failure
        if (random.nextDouble() < failureRate) {
            log.error("SHIPPING: Failed for order={} (simulated failure)", orderId);
            throw new ShippingException("Shipping service unavailable");
        }

        String shippingId = "SHIP-" + System.currentTimeMillis();
        shipments.put(shippingId,
                new ShippingRecord(shippingId, orderId, customerId, productId, quantity, "SCHEDULED"));

        log.info("SHIPPING: Scheduled successfully! shippingId={}", shippingId);
        return shippingId;
    }

    /**
     * Cancel scheduled shipping (compensation/rollback).
     */
    public void cancelShipping(String shippingId) {
        log.info("SHIPPING COMPENSATION: Cancelling shipment={}", shippingId);

        simulateLatency();

        ShippingRecord record = shipments.get(shippingId);
        if (record != null) {
            // Replace with cancelled status
            shipments.put(shippingId, new ShippingRecord(record.id, record.orderId,
                    record.customerId, record.productId, record.quantity, "CANCELLED"));
            log.info("SHIPPING COMPENSATION: Shipment {} cancelled", shippingId);
        } else {
            log.warn("SHIPPING COMPENSATION: Shipment {} not found", shippingId);
        }
    }

    public void setFailureRate(double rate) {
        this.failureRate = rate;
        log.info("Shipping failure rate set to {}%", rate * 100);
    }

    public double getFailureRate() {
        return failureRate;
    }

    private void simulateLatency() {
        try {
            Thread.sleep(100 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class ShippingRecord {
        String id, orderId, customerId, productId;
        int quantity;
        String status;

        public ShippingRecord(String id, String orderId, String customerId, String productId,
                int quantity, String status) {
            this.id = id;
            this.orderId = orderId;
            this.customerId = customerId;
            this.productId = productId;
            this.quantity = quantity;
            this.status = status;
        }
    }

    public static class ShippingException extends RuntimeException {
        public ShippingException(String message) {
            super(message);
        }
    }
}
