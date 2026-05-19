package org.codeart.saga.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.saga.model.Order;
import org.codeart.saga.model.OrderRepository;
import org.codeart.saga.model.SagaStatus;
import org.codeart.saga.orchestrator.SagaOrchestrator;
import org.codeart.saga.service.InventoryService;
import org.codeart.saga.service.PaymentService;
import org.codeart.saga.service.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Demo Controller - Demonstrates SAGA pattern behavior.
 */
@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
@Tag(name = "Demo", description = "Demonstrate SAGA pattern with success and failure scenarios")
public class DemoController {

    private final SagaOrchestrator sagaOrchestrator;
    private final OrderRepository orderRepository;
    private final PaymentService paymentService;
    private final InventoryService inventoryService;
    private final ShippingService shippingService;

    @GetMapping("/success-scenario")
    @Operation(summary = "Demo successful SAGA", description = "All services succeed: Payment -> Inventory -> Shipping -> COMPLETED")
    public ResponseEntity<Map<String, Object>> demoSuccess() {
        // Reset failure rates
        paymentService.setFailureRate(0.0);
        inventoryService.setFailureRate(0.0);
        shippingService.setFailureRate(0.0);

        Order order = Order.builder()
                .customerId("CUST-001")
                .productId("PROD-001")
                .quantity(2)
                .amount(199.99)
                .sagaStatus(SagaStatus.STARTED)
                .build();
        order = orderRepository.save(order);

        Order result = sagaOrchestrator.executeSaga(order);

        return ResponseEntity.ok(Map.of(
                "scenario", "SUCCESS",
                "description", "All SAGA steps completed successfully",
                "order", result,
                "steps", result.getSteps()));
    }

    @GetMapping("/payment-failure")
    @Operation(summary = "Demo payment failure", description = "Payment fails - no compensation needed (first step)")
    public ResponseEntity<Map<String, Object>> demoPaymentFailure() {
        paymentService.setFailureRate(1.0); // 100% failure
        inventoryService.setFailureRate(0.0);
        shippingService.setFailureRate(0.0);

        Order order = Order.builder()
                .customerId("CUST-002")
                .productId("PROD-001")
                .quantity(1)
                .amount(99.99)
                .sagaStatus(SagaStatus.STARTED)
                .build();
        order = orderRepository.save(order);

        Order result = sagaOrchestrator.executeSaga(order);

        // Reset
        paymentService.setFailureRate(0.0);

        return ResponseEntity.ok(Map.of(
                "scenario", "PAYMENT_FAILURE",
                "description", "Payment failed - SAGA stopped, no compensation needed",
                "order", result,
                "steps", result.getSteps()));
    }

    @GetMapping("/inventory-failure")
    @Operation(summary = "Demo inventory failure with compensation", description = "Payment succeeds, Inventory fails -> Refund payment")
    public ResponseEntity<Map<String, Object>> demoInventoryFailure() {
        paymentService.setFailureRate(0.0);
        inventoryService.setFailureRate(1.0); // 100% failure
        shippingService.setFailureRate(0.0);

        Order order = Order.builder()
                .customerId("CUST-003")
                .productId("PROD-001")
                .quantity(1)
                .amount(149.99)
                .sagaStatus(SagaStatus.STARTED)
                .build();
        order = orderRepository.save(order);

        Order result = sagaOrchestrator.executeSaga(order);

        // Reset
        inventoryService.setFailureRate(0.0);

        return ResponseEntity.ok(Map.of(
                "scenario", "INVENTORY_FAILURE_WITH_COMPENSATION",
                "description", "Inventory failed - Payment was refunded (compensation)",
                "order", result,
                "steps", result.getSteps()));
    }

    @GetMapping("/shipping-failure")
    @Operation(summary = "Demo shipping failure with full compensation", description = "Payment OK, Inventory OK, Shipping fails -> Release inventory, Refund payment")
    public ResponseEntity<Map<String, Object>> demoShippingFailure() {
        paymentService.setFailureRate(0.0);
        inventoryService.setFailureRate(0.0);
        shippingService.setFailureRate(1.0); // 100% failure

        Order order = Order.builder()
                .customerId("CUST-004")
                .productId("PROD-002")
                .quantity(1)
                .amount(299.99)
                .sagaStatus(SagaStatus.STARTED)
                .build();
        order = orderRepository.save(order);

        Order result = sagaOrchestrator.executeSaga(order);

        // Reset
        shippingService.setFailureRate(0.0);

        return ResponseEntity.ok(Map.of(
                "scenario", "SHIPPING_FAILURE_WITH_FULL_COMPENSATION",
                "description", "Shipping failed - Inventory released AND Payment refunded",
                "order", result,
                "steps", result.getSteps()));
    }

    @GetMapping("/out-of-stock")
    @Operation(summary = "Demo out of stock scenario", description = "Payment succeeds, but product is out of stock -> Refund payment")
    public ResponseEntity<Map<String, Object>> demoOutOfStock() {
        paymentService.setFailureRate(0.0);
        inventoryService.setFailureRate(0.0);
        shippingService.setFailureRate(0.0);

        // PROD-004 has 0 inventory
        Order order = Order.builder()
                .customerId("CUST-005")
                .productId("PROD-004") // Out of stock product
                .quantity(1)
                .amount(199.99)
                .sagaStatus(SagaStatus.STARTED)
                .build();
        order = orderRepository.save(order);

        Order result = sagaOrchestrator.executeSaga(order);

        return ResponseEntity.ok(Map.of(
                "scenario", "OUT_OF_STOCK",
                "description", "Product out of stock - Payment refunded",
                "order", result,
                "steps", result.getSteps()));
    }

    @GetMapping("/multiple-orders")
    @Operation(summary = "Demo multiple orders with mixed results", description = "Create 5 orders with 30% failure rate")
    public ResponseEntity<Map<String, Object>> demoMultipleOrders() {
        paymentService.setFailureRate(0.1);
        inventoryService.setFailureRate(0.1);
        shippingService.setFailureRate(0.1);

        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0, failed = 0, compensated = 0;

        for (int i = 0; i < 5; i++) {
            Order order = Order.builder()
                    .customerId("CUST-BATCH")
                    .productId("PROD-001")
                    .quantity(1)
                    .amount(50.0 + i * 10)
                    .sagaStatus(SagaStatus.STARTED)
                    .build();
            order = orderRepository.save(order);
            Order result = sagaOrchestrator.executeSaga(order);

            results.add(Map.of(
                    "orderId", result.getId(),
                    "status", result.getSagaStatus(),
                    "failure", result.getFailureReason() != null ? result.getFailureReason() : "None"));

            if (result.getSagaStatus() == SagaStatus.COMPLETED)
                success++;
            else if (result.getSagaStatus() == SagaStatus.COMPENSATED)
                compensated++;
            else
                failed++;
        }

        // Reset
        paymentService.setFailureRate(0.0);
        inventoryService.setFailureRate(0.0);
        shippingService.setFailureRate(0.0);

        return ResponseEntity.ok(Map.of(
                "scenario", "MULTIPLE_ORDERS",
                "totalOrders", 5,
                "succeeded", success,
                "failed", failed,
                "compensated", compensated,
                "results", results));
    }

    @GetMapping("/inventory")
    @Operation(summary = "Get current inventory levels")
    public ResponseEntity<Map<String, Integer>> getInventory() {
        return ResponseEntity.ok(inventoryService.getInventoryLevels());
    }

    @PostMapping("/simulate/failure-rates")
    @Operation(summary = "Set failure rates for all services")
    public ResponseEntity<Map<String, Object>> setFailureRates(
            @RequestParam(defaultValue = "0.0") double payment,
            @RequestParam(defaultValue = "0.0") double inventory,
            @RequestParam(defaultValue = "0.0") double shipping) {

        paymentService.setFailureRate(payment);
        inventoryService.setFailureRate(inventory);
        shippingService.setFailureRate(shipping);

        return ResponseEntity.ok(Map.of(
                "paymentFailureRate", payment,
                "inventoryFailureRate", inventory,
                "shippingFailureRate", shipping));
    }
}
