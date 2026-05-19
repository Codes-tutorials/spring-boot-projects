# Kafka Log Aggregator - Production Ready

A centralized logging solution using Apache Kafka for **multiple microservices** with **multiple instances** support.

## Features

- 🚀 **Multi-Service Support** - Dynamic service name from configuration
- 📦 **Multi-Instance Scaling** - Unique instance ID per app instance
- 🔀 **Partition-Based Distribution** - Service-based partitioning for ordered processing
- ⚡ **Concurrent Consumption** - Multiple consumer threads with partition assignment
- 💀 **Dead Letter Topic** - Failed messages sent to `.DLT` topic
- 📊 **Distributed Tracing** - traceId, spanId, correlationId support
- 📈 **Metrics** - Micrometer/Prometheus integration

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│ Order Service   │    │ Payment Service │    │ User Service    │
│  (Instance 1)   │    │  (Instance 1)   │    │  (Instance 1)   │
│  (Instance 2)   │    │  (Instance 2)   │    │  (Instance 2)   │
└────────┬────────┘    └────────┬────────┘    └────────┬────────┘
         │                      │                      │
         └──────────────────────┼──────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │    Kafka Cluster      │
                    │   system-logs (P0-P5) │
                    │   [6 partitions]      │
                    └───────────┬───────────┘
                                │
         ┌──────────────────────┼──────────────────────┐
         │                      │                      │
┌────────▼────────┐  ┌─────────▼────────┐  ┌─────────▼────────┐
│ Consumer Inst 1 │  │ Consumer Inst 2 │  │ Consumer Inst 3 │
│   (P0, P1)      │  │   (P2, P3)      │  │   (P4, P5)      │
└─────────────────┘  └─────────────────┘  └─────────────────┘
```

## Quick Start

### 1. Start Kafka Cluster
```bash
cd kafka-log-aggregator
docker-compose up -d
```

### 2. Build the Project
```bash
mvn clean install
```

### 3. Run Multiple Producer Instances
```bash
# Terminal 1 - Order Service Instance 1
cd log-producer
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081 --SERVICE_NAME=order-service"

# Terminal 2 - Order Service Instance 2
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8083 --SERVICE_NAME=order-service"

# Terminal 3 - Payment Service
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8084 --SERVICE_NAME=payment-service"
```

### 4. Run Multiple Consumer Instances
```bash
# Terminal 1 - Consumer Instance 1
cd log-consumer
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8082"

# Terminal 2 - Consumer Instance 2
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8085"
```

### 5. Send Test Logs
```bash
# Send a log
curl -X POST http://localhost:8081/api/logs \
  -H "Content-Type: application/json" \
  -d '{"level":"INFO","message":"Order created successfully"}'

# Send with trace context
curl -X POST http://localhost:8081/api/logs/traced \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: abc123" \
  -d '{"level":"ERROR","message":"Payment failed"}'
```

### 6. Monitor
- **Kafka UI**: http://localhost:8088
- **Producer Metrics**: http://localhost:8081/actuator/prometheus
- **Consumer Metrics**: http://localhost:8082/actuator/prometheus

## Multi-Instance Behavior

| Feature | How It Works |
|---------|--------------|
| **Partition Assignment** | Kafka automatically assigns partitions to consumer instances |
| **Load Balancing** | Logs distributed across partitions by service name hash |
| **Failover** | If consumer dies, Kafka rebalances partitions to remaining consumers |
| **Ordering** | Logs from same service go to same partition → ordered processing |

## Module Structure

```
kafka-log-aggregator/
├── log-model/         # Shared DTOs (LogEvent, LogLevel, KafkaTopics)
├── log-producer/      # Producer library with partitioning
├── log-consumer/      # Consumer with concurrent listeners
├── docker-compose.yml # Kafka cluster setup
└── pom.xml           # Parent POM
```

## Configuration

### Producer (application.yml)
```yaml
spring:
  application:
    name: ${SERVICE_NAME:my-service}
log-producer:
  instance-id: ${INSTANCE_ID:${random.uuid}}
  environment: ${ENVIRONMENT:dev}
```

### Consumer (application.yml)
```yaml
spring:
  kafka:
    consumer:
      group-id: log-aggregator-group
    listener:
      concurrency: 3  # 3 threads per consumer instance
```
