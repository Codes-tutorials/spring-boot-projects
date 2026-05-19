package org.codeart.saga.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.saga.model.Order;
import org.codeart.saga.model.OrderRepository;
import org.codeart.saga.model.SagaStatus;
import org.codeart.saga.orchestrator.SagaOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Order Controller - Entry point for SAGA execution.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Order API", description = "Create orders that execute SAGA transactions")
public class OrderController {

    private final SagaOrchestrator sagaOrchestrator;
    private final OrderRepository orderRepository;

    @PostMapping
    @Operation(summary = "Create order (execute SAGA)", description = "Creates an order and executes the SAGA: Payment -> Inventory -> Shipping")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        Order order = Order.builder()
                .customerId(request.customerId())
                .productId(request.productId())
                .quantity(request.quantity())
                .amount(request.amount())
                .sagaStatus(SagaStatus.STARTED)
                .build();

        order = orderRepository.save(order);

        // Execute SAGA
        Order result = sagaOrchestrator.executeSaga(order);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID")
    public ResponseEntity<Order> getOrder(@PathVariable String id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    @Operation(summary = "Get all orders")
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderRepository.findAll());
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get orders by SAGA status")
    public ResponseEntity<List<Order>> getOrdersByStatus(@PathVariable SagaStatus status) {
        return ResponseEntity.ok(orderRepository.findBySagaStatus(status));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get orders by customer")
    public ResponseEntity<List<Order>> getOrdersByCustomer(@PathVariable String customerId) {
        return ResponseEntity.ok(orderRepository.findByCustomerId(customerId));
    }

    @GetMapping("/{id}/steps")
    @Operation(summary = "Get SAGA execution steps for an order")
    public ResponseEntity<Map<String, Object>> getOrderSteps(@PathVariable String id) {
        return orderRepository.findById(id)
                .map(order -> ResponseEntity.ok(Map.of(
                        "orderId", order.getId(),
                        "sagaStatus", order.getSagaStatus(),
                        "failureReason", order.getFailureReason() != null ? order.getFailureReason() : "None",
                        "steps", order.getSteps())))
                .orElse(ResponseEntity.notFound().build());
    }

    public record CreateOrderRequest(
            String customerId,
            String productId,
            int quantity,
            double amount) {
    }
}
