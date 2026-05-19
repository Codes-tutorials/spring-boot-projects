package org.codeart.circuitbreaker.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.circuitbreaker.client.ExternalPaymentClient;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Payment Service with full Resilience4j protection:
 * - Circuit Breaker: Prevents calls when service is down
 * - Retry: Automatic retry with exponential backoff
 * - Rate Limiter: Limits requests per second
 * - Bulkhead: Limits concurrent calls
 * - Time Limiter: Timeout protection
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final ExternalPaymentClient paymentClient;

    /**
     * Process payment with Circuit Breaker protection.
     * Fallback returns a cached/default response when circuit is open.
     */
    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    @Retry(name = "paymentService")
    @RateLimiter(name = "paymentService")
    @Bulkhead(name = "paymentService")
    public PaymentResult processPayment(String orderId, double amount) {
        log.info("Processing payment for order: {}, amount: {}", orderId, amount);

        String transactionId = paymentClient.processPayment(orderId, amount);

        return PaymentResult.builder()
                .orderId(orderId)
                .transactionId(transactionId)
                .amount(amount)
                .status("SUCCESS")
                .timestamp(Instant.now())
                .source("LIVE")
                .build();
    }

    /**
     * Async payment with Time Limiter (timeout protection).
     */
    @CircuitBreaker(name = "paymentService", fallbackMethod = "asyncPaymentFallback")
    @TimeLimiter(name = "paymentService")
    @Bulkhead(name = "paymentService", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<PaymentResult> processPaymentAsync(String orderId, double amount) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("Async payment for order: {}", orderId);
            String transactionId = paymentClient.processPayment(orderId, amount);

            return PaymentResult.builder()
                    .orderId(orderId)
                    .transactionId(transactionId)
                    .amount(amount)
                    .status("SUCCESS")
                    .timestamp(Instant.now())
                    .source("LIVE_ASYNC")
                    .build();
        });
    }

    /**
     * Fallback when circuit is OPEN or calls fail.
     * Can return cached data, queue for later, or graceful degradation.
     */
    private PaymentResult paymentFallback(String orderId, double amount, Throwable t) {
        log.warn("Payment fallback triggered for order: {}, reason: {}",
                orderId, t.getMessage());

        String fallbackReason;
        if (t instanceof CallNotPermittedException) {
            fallbackReason = "Circuit breaker OPEN - service unavailable";
        } else {
            fallbackReason = "Service error: " + t.getMessage();
        }

        return PaymentResult.builder()
                .orderId(orderId)
                .transactionId("PENDING-" + System.currentTimeMillis())
                .amount(amount)
                .status("PENDING")
                .timestamp(Instant.now())
                .source("FALLBACK")
                .fallbackReason(fallbackReason)
                .build();
    }

    /**
     * Async fallback.
     */
    private CompletableFuture<PaymentResult> asyncPaymentFallback(String orderId, double amount, Throwable t) {
        log.warn("Async payment fallback for order: {}", orderId);
        return CompletableFuture.completedFuture(
                paymentFallback(orderId, amount, t));
    }

    /**
     * Payment result DTO.
     */
    @lombok.Builder
    @lombok.Data
    public static class PaymentResult {
        private String orderId;
        private String transactionId;
        private double amount;
        private String status;
        private Instant timestamp;
        private String source; // LIVE, FALLBACK, CACHED
        private String fallbackReason;
    }
}
