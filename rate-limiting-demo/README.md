# Rate Limiting Demo - Production Ready

A comprehensive Spring Boot application demonstrating production-ready rate limiting using **Bucket4j**, **Resilience4j**, and optional **Redis** for distributed rate limiting.

## Features

- 🪣 **Bucket4j Integration** - Token bucket algorithm for global/IP-based rate limiting
- 🔄 **Resilience4j Support** - Method-level rate limiting with annotations
- 📊 **Tiered API Keys** - Different rate limits for Free, Basic, and Premium tiers
- 🔴 **Redis Support** - Distributed rate limiting for multi-instance deployments
- 📈 **Metrics & Monitoring** - Micrometer/Prometheus integration
- 📝 **OpenAPI Documentation** - Swagger UI for API exploration
- 🧪 **Comprehensive Tests** - Unit and integration tests with Testcontainers

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- Docker (optional, for Redis)

### Run Locally (In-Memory Mode)

```bash
cd rate-limiting-demo
mvn spring-boot:run
```

### Run with Redis (Distributed Mode)

```bash
# Start Redis
docker-compose up -d

# Run with Redis profile
mvn spring-boot:run -Dspring-boot.run.profiles=redis
```

### Access the Application

| URL | Description |
|-----|-------------|
| http://localhost:8080/swagger-ui.html | Swagger UI |
| http://localhost:8080/actuator/prometheus | Prometheus Metrics |
| http://localhost:8080/api/v1/public | Public endpoint (rate limited) |

## Rate Limit Tiers

| Tier | Requests/Minute | Burst Capacity | API Key Example |
|------|-----------------|----------------|-----------------|
| Anonymous | 60 | 60 | (no key) |
| Free | 10 | 15 | `free-key-001` |
| Basic | 100 | 150 | `basic-key-001` |
| Premium | 1000 | 1500 | `premium-key-001` |

## API Endpoints

### Demo Endpoints

| Method | Endpoint | Rate Limit Strategy |
|--------|----------|---------------------|
| GET | `/api/v1/public` | Global Bucket4j Filter (tier-based) |
| GET | `/api/v1/protected` | Resilience4j `@RateLimiter` (5/min) |
| GET | `/api/v1/relaxed` | Resilience4j `@RateLimiter` (100/min) |
| GET | `/api/v1/custom` | Custom `@RateLimit` (3/10sec) |
| GET | `/api/v1/api-key-limited` | `@RateLimit` by API Key (5/min) |
| GET | `/api/v1/slow` | `@RateLimit` (2/30sec) |

### Admin Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/admin/config` | Get rate limit configuration |
| GET | `/api/v1/admin/metrics` | Get rate limit metrics |
| GET | `/api/v1/admin/status/{key}` | Get status for a key |
| DELETE | `/api/v1/admin/limits/{key}` | Reset rate limit for a key |

## Testing Rate Limits

### Test with curl

```bash
# Test public endpoint (hit the limit)
for i in {1..15}; do curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/api/v1/public; done

# Test with API key
curl -H "X-Api-Key: premium-key-001" http://localhost:8080/api/v1/public

# View rate limit headers
curl -i http://localhost:8080/api/v1/public
```

### Rate Limit Headers

All responses include rate limit information:

```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 99
X-RateLimit-Reset: 1705350060
X-RateLimit-Tier: basic
```

## Configuration

### application.yml

```yaml
rate-limit:
  enabled: true
  storage-type: in-memory  # or 'redis'
  default-limit: 60
  tiers:
    free:
      requests-per-minute: 10
      burst-capacity: 15
    basic:
      requests-per-minute: 100
      burst-capacity: 150
    premium:
      requests-per-minute: 1000
      burst-capacity: 1500
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     HTTP Request                             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              Bucket4jRateLimitFilter (Global)                │
│    ┌──────────────┐    ┌──────────────┐                     │
│    │ IpKeyResolver│    │ApiKeyResolver│                     │
│    └──────────────┘    └──────────────┘                     │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│              RateLimitInterceptor (Optional)                 │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      Controller Layer                        │
│    ┌─────────────────┐    ┌───────────────────┐             │
│    │ @RateLimiter    │    │ @RateLimit        │             │
│    │ (Resilience4j)  │    │ (Custom AOP)      │             │
│    └─────────────────┘    └───────────────────┘             │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                     RateLimitService                         │
│    ┌──────────────────┐    ┌──────────────────┐             │
│    │  In-Memory       │ OR │  Redis (Bucket4j)│             │
│    │  ConcurrentMap   │    │  ProxyManager    │             │
│    └──────────────────┘    └──────────────────┘             │
└─────────────────────────────────────────────────────────────┘
```

## License

MIT License
