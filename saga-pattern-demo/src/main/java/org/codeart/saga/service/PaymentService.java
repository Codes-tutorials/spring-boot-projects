package org.codeart.saga.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Simulated Payment Service.
 * In real microservices, this would be a separate service.
 */
@Slf4j
@Service
public class PaymentService {

    private final Map<String, PaymentRecord> payments = new ConcurrentHashMap<>();
    private final Random random = new Random();

    // Configurable failure rate for demo
    private volatile double failureRate = 0.0;

    /**
     * Process payment for an order.
     * 
     * @return Payment ID on success
     * @throws PaymentException on failure
     */
    public String processPayment(String orderId, String customerId, double amount) {
        log.info("PAYMENT: Processing payment for order={}, customer={}, amount={}",
                orderId, customerId, amount);

        // Simulate processing time
        simulateLatency();

        // Simulate failure
        if (random.nextDouble() < failureRate) {
            log.error("PAYMENT: Failed for order={} (simulated failure)", orderId);
            throw new PaymentException("Payment declined: Insufficient funds");
        }

        String paymentId = "PAY-" + System.currentTimeMillis();
        payments.put(paymentId, new PaymentRecord(paymentId, orderId, customerId, amount, "COMPLETED"));

        log.info("PAYMENT: Success! paymentId={} for order={}", paymentId, orderId);
        return paymentId;
    }

    /**
     * Refund/compensate a payment (rollback).
     */
    public void refundPayment(String paymentId) {
        log.info("PAYMENT COMPENSATION: Refunding payment={}", paymentId);

        simulateLatency();

        PaymentRecord record = payments.get(paymentId);
        if (record != null) {
            // Replace with refunded status
            payments.put(paymentId, new PaymentRecord(record.id(), record.orderId(),
                    record.customerId(), record.amount(), "REFUNDED"));
            log.info("PAYMENT COMPENSATION: Refund completed for payment={}", paymentId);
        } else {
            log.warn("PAYMENT COMPENSATION: Payment {} not found, nothing to refund", paymentId);
        }
    }

    public void setFailureRate(double rate) {
        this.failureRate = rate;
        log.info("Payment failure rate set to {}%", rate * 100);
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

    public record PaymentRecord(String id, String orderId, String customerId, double amount, String status) {
    }

    public static class PaymentException extends RuntimeException {
        public PaymentException(String message) {
            super(message);
        }
    }
}
