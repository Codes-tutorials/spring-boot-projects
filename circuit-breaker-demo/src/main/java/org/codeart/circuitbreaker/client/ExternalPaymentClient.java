package org.codeart.circuitbreaker.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates an external payment service that can fail.
 * Use this to demonstrate circuit breaker behavior.
 */
@Slf4j
@Component
public class ExternalPaymentClient {

    private final Random random = new Random();
    private final AtomicInteger callCount = new AtomicInteger(0);

    // Control failure behavior
    private volatile double failureRate = 0.0; // 0.0 to 1.0
    private volatile long latencyMs = 100;
    private volatile boolean simulateTimeout = false;

    /**
     * Simulate payment processing.
     * Can be configured to fail, timeout, or succeed.
     */
    public String processPayment(String orderId, double amount) {
        int call = callCount.incrementAndGet();
        log.info("Payment call #{}: orderId={}, amount={}", call, orderId, amount);

        // Simulate latency
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate timeout
        if (simulateTimeout) {
            try {
                Thread.sleep(10000); // 10 second delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Simulate failure
        if (random.nextDouble() < failureRate) {
            log.error("Payment call #{} FAILED (simulated)", call);
            throw new RuntimeException("Payment service unavailable");
        }

        log.info("Payment call #{} SUCCESS", call);
        return "PAY-" + System.currentTimeMillis();
    }

    /**
     * Set failure rate (0.0 = never fail, 1.0 = always fail).
     */
    public void setFailureRate(double rate) {
        this.failureRate = Math.max(0.0, Math.min(1.0, rate));
        log.info("Payment failure rate set to: {}%", failureRate * 100);
    }

    /**
     * Set response latency in milliseconds.
     */
    public void setLatency(long ms) {
        this.latencyMs = ms;
        log.info("Payment latency set to: {}ms", latencyMs);
    }

    /**
     * Enable/disable timeout simulation.
     */
    public void setSimulateTimeout(boolean timeout) {
        this.simulateTimeout = timeout;
        log.info("Payment timeout simulation: {}", timeout);
    }

    /**
     * Get total call count.
     */
    public int getCallCount() {
        return callCount.get();
    }

    /**
     * Reset call count.
     */
    public void resetCallCount() {
        callCount.set(0);
    }

    /**
     * Get current configuration.
     */
    public ClientConfig getConfig() {
        return new ClientConfig(failureRate, latencyMs, simulateTimeout, callCount.get());
    }

    public record ClientConfig(double failureRate, long latencyMs, boolean simulateTimeout, int callCount) {
    }
}
