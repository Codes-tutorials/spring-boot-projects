# SAGA Pattern Demo

Production-ready **SAGA Pattern** implementation for distributed transactions in Spring Boot microservices.

## What is SAGA?

SAGA is a pattern for managing distributed transactions across multiple services. Instead of 2PC (two-phase commit), SAGA uses:

1. **Local transactions** in each service
2. **Compensation transactions** (rollback) if any step fails

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SAGA ORCHESTRATOR                           │
│                                                                     │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐     │
│  │ Payment  │───▶│Inventory │───▶│ Shipping │───▶│ COMPLETE │     │
│  │ Service  │    │ Service  │    │ Service  │    │          │     │
│  └────┬─────┘    └────┬─────┘    └────┬─────┘    └──────────┘     │
│       │               │               │                            │
│       ▼               ▼               ▼                            │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐                     │
│  │  Refund  │◀───│ Release  │◀───│  Cancel  │  (COMPENSATION)     │
│  │          │    │ Inventory│    │ Shipping │                     │
│  └──────────┘    └──────────┘    └──────────┘                     │
└─────────────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
cd saga-pattern-demo
mvn spring-boot:run

# Swagger UI
http://localhost:8080/swagger-ui.html

# H2 Console
http://localhost:8080/h2-console
```

## Demo Scenarios

### 1. Successful Order
```bash
curl http://localhost:8080/api/demo/success-scenario
```
**Flow**: Payment ✓ → Inventory ✓ → Shipping ✓ → **COMPLETED**

### 2. Payment Failure
```bash
curl http://localhost:8080/api/demo/payment-failure
```
**Flow**: Payment ✗ → **FAILED** (no compensation needed)

### 3. Inventory Failure with Compensation
```bash
curl http://localhost:8080/api/demo/inventory-failure
```
**Flow**: Payment ✓ → Inventory ✗ → **Refund Payment** → COMPENSATED

### 4. Shipping Failure with Full Compensation
```bash
curl http://localhost:8080/api/demo/shipping-failure
```
**Flow**: Payment ✓ → Inventory ✓ → Shipping ✗ → **Release Inventory** → **Refund Payment** → COMPENSATED

### 5. Out of Stock
```bash
curl http://localhost:8080/api/demo/out-of-stock
```
**Flow**: Payment ✓ → Inventory ✗ (no stock) → **Refund Payment** → COMPENSATED

## Create Custom Order

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerId": "CUST-123",
    "productId": "PROD-001",
    "quantity": 2,
    "amount": 199.99
  }'
```

## View Order Steps

```bash
curl http://localhost:8080/api/orders/{orderId}/steps
```

## Key Classes

| Class | Purpose |
|-------|---------|
| `SagaOrchestrator` | Coordinates SAGA steps and compensation |
| `PaymentService` | Process/refund payments |
| `InventoryService` | Reserve/release inventory |
| `ShippingService` | Schedule/cancel shipments |
| `Order` | Aggregate with SAGA status and step history |
