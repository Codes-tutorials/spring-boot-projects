# Multi-Level Cache Demo

Production-ready caching with **Caffeine (L1)** + **Redis (L2)** demonstrating cache patterns and invalidation strategies.

## Features

- 🏎️ **Multi-Level Cache**: L1 (Caffeine, local) + L2 (Redis, distributed)
- 📚 **Cache Patterns**: Cache-Aside, Write-Through, Write-Behind
- 🔄 **Invalidation**: TTL, Event-based (Redis Pub/Sub), Manual
- 📊 **Metrics**: Micrometer/Prometheus integration
- 📖 **Swagger UI**: Interactive API documentation

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                       Application                            │
└──────────────────────────┬──────────────────────────────────┘
                           │
            ┌──────────────▼──────────────┐
            │   L1: Caffeine (Local)      │  ← ~1ms, 1000 entries
            │   TTL: 60s                  │
            └──────────────┬──────────────┘
                           │ miss
            ┌──────────────▼──────────────┐
            │   L2: Redis (Distributed)   │  ← ~5ms, shared
            │   TTL: 5-60 min             │
            └──────────────┬──────────────┘
                           │ miss
            ┌──────────────▼──────────────┐
            │        Database             │  ← ~50-100ms
            └─────────────────────────────┘
```

## Quick Start

```bash
# Start Redis
docker-compose up -d

# Run the application
mvn spring-boot:run

# Access Swagger UI
http://localhost:8080/swagger-ui.html
```

## Cache Patterns

### 1. Cache-Aside (Lazy Loading)
```
GET /api/products/cache-aside/{id}
POST /api/products/cache-aside
PUT /api/products/cache-aside/{id}
```
- **Read**: Cache → Miss → Load DB → Update Cache
- **Write**: Update DB → Evict Cache

### 2. Write-Through
```
GET /api/products/write-through/{id}
POST /api/products/write-through
```
- **Write**: Update Cache + DB synchronously
- **Read**: Always from cache (populated on write)

### 3. Write-Behind (Async)
```
GET /api/products/write-behind/{id}
POST /api/products/write-behind
GET /api/products/write-behind/pending
POST /api/products/write-behind/flush
```
- **Write**: Update cache → Queue async DB write
- **Flush**: Every 5 seconds or on demand

## Invalidation Strategies

| Strategy | How | Use Case |
|----------|-----|----------|
| **TTL** | Caffeine/Redis expiry | Time-sensitive |
| **Event-based** | Redis Pub/Sub | Multi-instance |
| **Manual** | `/api/admin/cache/evict/{cache}/{key}` | Explicit |
| **LRU** | Caffeine eviction policy | Memory limit |

## Admin Endpoints

```
GET  /api/admin/cache/config     # View configuration
GET  /api/admin/cache/stats      # View statistics
POST /api/admin/cache/evict/{cache}/{key}  # Evict entry
POST /api/admin/cache/clear/{cache}        # Clear cache
POST /api/admin/cache/clear-all            # Clear all
```

## Configuration

```yaml
cache:
  multi-level:
    enabled: true
  caffeine:
    default-spec: maximumSize=1000,expireAfterWrite=60s
  redis:
    default-ttl: 300
  write-behind:
    flush-interval-ms: 5000
```

## Monitoring

- **H2 Console**: http://localhost:8080/h2-console
- **Redis Commander**: http://localhost:8085
- **Prometheus Metrics**: http://localhost:8080/actuator/prometheus
- **Cache Stats**: http://localhost:8080/actuator/caches
