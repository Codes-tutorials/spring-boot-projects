# CQRS Pattern Demo

Production-ready **CQRS (Command Query Responsibility Segregation)** implementation with Event Sourcing in Spring Boot.

## What is CQRS?

CQRS separates **reads** (queries) from **writes** (commands):

```
┌─────────────────────────────────────────────────────────────────────┐
│                           CLIENT                                    │
│                    ┌─────────┴─────────┐                           │
│                    ▼                   ▼                            │
│           ┌─────────────┐       ┌─────────────┐                    │
│           │  COMMANDS   │       │  QUERIES    │                    │
│           │  (Write)    │       │  (Read)     │                    │
│           └──────┬──────┘       └──────┬──────┘                    │
│                  ▼                     ▼                            │
│           ┌─────────────┐       ┌─────────────┐                    │
│           │  Command    │       │  Query      │                    │
│           │  Handler    │       │  Handler    │                    │
│           └──────┬──────┘       └──────┬──────┘                    │
│                  ▼                     ▼                            │
│           ┌─────────────┐       ┌─────────────┐                    │
│           │  Aggregate  │       │  Read Model │                    │
│           │  (Domain)   │       │  (View)     │                    │
│           └──────┬──────┘       └──────┬──────┘                    │
│                  │                     ▲                            │
│                  ▼                     │                            │
│           ┌─────────────┐       ┌──────┴──────┐                    │
│           │ Event Store │──────▶│ Projection  │                    │
│           └─────────────┘       └─────────────┘                    │
└─────────────────────────────────────────────────────────────────────┘
```

## Quick Start

```bash
cd cqrs-pattern-demo
mvn spring-boot:run

# Swagger UI
http://localhost:8080/swagger-ui.html

# H2 Console
http://localhost:8080/h2-console
```

## API Endpoints

### Commands (Write Side)
```bash
# Create product
curl -X POST http://localhost:8080/api/commands/products \
  -H "Content-Type: application/json" \
  -d '{"name":"iPhone","description":"Smartphone","price":999,"quantity":50,"category":"Electronics"}'

# Update price
curl -X PUT "http://localhost:8080/api/commands/products/{id}/price?newPrice=899"

# Update quantity
curl -X PUT "http://localhost:8080/api/commands/products/{id}/quantity?change=-5&reason=Sold"

# Delete product
curl -X DELETE http://localhost:8080/api/commands/products/{id}
```

### Queries (Read Side)
```bash
# Get all products
curl http://localhost:8080/api/queries/products

# Get by ID
curl http://localhost:8080/api/queries/products/{id}

# Search by name
curl "http://localhost:8080/api/queries/products/search?q=iPhone"

# Get by category
curl http://localhost:8080/api/queries/products/category/Electronics

# Get by stock status
curl http://localhost:8080/api/queries/products/stock-status/LOW_STOCK

# Dashboard summary
curl http://localhost:8080/api/queries/products/dashboard
```

### Demo
```bash
# Seed sample data
curl http://localhost:8080/api/demo/seed

# Full CQRS demo
curl http://localhost:8080/api/demo/cqrs-demo

# View all events
curl http://localhost:8080/api/demo/events
```

## Key Components

| Component | Purpose |
|-----------|---------|
| **ProductCommand** | Commands (CreateProduct, UpdatePrice, etc.) |
| **ProductCommandHandler** | Processes commands, updates aggregates |
| **ProductAggregate** | Domain logic, business rules, event sourcing |
| **EventStore** | Stores events, notifies projections |
| **ProductProjection** | Updates read model from events |
| **ProductView** | Read-optimized data model |
| **ProductQueryHandler** | Executes queries on read model |
