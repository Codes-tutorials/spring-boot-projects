# Circuit Breaker Demo

Production-ready **Circuit Breaker** implementation with **Resilience4j** in Spring Boot 3.

## Features

| Pattern | Description | Config |
|---------|-------------|--------|
| **Circuit Breaker** | Prevent cascade failures | 50% failure threshold, 10s open |
| **Retry** | Auto retry with backoff | 3 attempts, exponential 2x |
| **Rate Limiter** | Request throttling | 5 req/sec |
| **Bulkhead** | Thread isolation | 5 concurrent calls |
| **Time Limiter** | Timeout protection | 2s timeout |

## Quick Start

```bash
# Run the application
cd circuit-breaker-demo
mvn spring-boot:run

# Open Swagger UI
http://localhost:8080/swagger-ui.html
```

## Demo Endpoints

### Circuit Breaker Lifecycle
```bash
# See CLOSED -> OPEN -> HALF_OPEN transitions
curl http://localhost:8080/api/demo/circuit-breaker-lifecycle
```

### Cascade Prevention
```bash
# See how 100 requests only make ~10 actual calls when service is down
curl http://localhost:8080/api/demo/cascade-prevention
```

### Simulate Failures
```bash
# Set 80% failure rate
curl -X POST "http://localhost:8080/api/admin/circuit-breaker/simulate/failure-rate?rate=0.8"

# Make payment calls (watch circuit open)
curl -X POST "http://localhost:8080/api/payment/process?orderId=123&amount=100"

# Check circuit state
curl http://localhost:8080/api/admin/circuit-breaker/status/paymentService
```

### Force Circuit State
```bash
# Force open
curl -X POST http://localhost:8080/api/admin/circuit-breaker/paymentService/open

# Force closed
curl -X POST http://localhost:8080/api/admin/circuit-breaker/paymentService/close
```

## Circuit Breaker States

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│    CLOSED ──────────> OPEN ──────────> HALF_OPEN           │
│      │    (failures)    │   (wait 10s)    │                │
│      │                  │                 │                │
│      │                  └────────────────┘                 │
│      │                    (if fails again)                  │
│      │                                    │                 │
│      └────────────────────────────────────┘                 │
│                (if succeeds)                                │
└─────────────────────────────────────────────────────────────┘
```

## Actuator Endpoints

```bash
# Health with circuit breaker details
curl http://localhost:8080/actuator/health

# Circuit breaker metrics
curl http://localhost:8080/actuator/circuitbreakers

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus | grep resilience4j
```

## Key Classes

| Class | Purpose |
|-------|---------|
| `PaymentService` | Service with all Resilience4j annotations |
| `ExternalPaymentClient` | Simulated external service with configurable failures |
| `CircuitBreakerMonitorService` | Monitor and control circuit breakers |
| `DemoController` | Demo endpoints showing patterns in action |

## Configuration

See `application.yml` for full Resilience4j configuration:
- Circuit breaker window sizes and thresholds
- Retry attempts and backoff
- Rate limiter limits
- Bulkhead concurrency
- Time limiter timeouts
