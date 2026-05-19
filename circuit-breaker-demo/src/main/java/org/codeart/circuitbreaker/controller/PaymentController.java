package org.codeart.circuitbreaker.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.circuitbreaker.service.PaymentService;
import org.codeart.circuitbreaker.service.PaymentService.PaymentResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Payment API with Circuit Breaker protection.
 */
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
@Tag(name = "Payment API", description = "Payment endpoint protected by circuit breaker")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/process")
    @Operation(summary = "Process payment", description = "Process payment with circuit breaker, retry, and rate limiting protection")
    public ResponseEntity<PaymentResult> processPayment(
            @RequestParam String orderId,
            @RequestParam double amount) {

        PaymentResult result = paymentService.processPayment(orderId, amount);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/process-async")
    @Operation(summary = "Process payment (async)", description = "Async payment with time limiter protection")
    public CompletableFuture<ResponseEntity<PaymentResult>> processPaymentAsync(
            @RequestParam String orderId,
            @RequestParam double amount) {

        return paymentService.processPaymentAsync(orderId, amount)
                .thenApply(ResponseEntity::ok);
    }

    @GetMapping("/test")
    @Operation(summary = "Test endpoint to verify API is working")
    public ResponseEntity<Map<String, String>> test() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Payment API is running"));
    }
}
