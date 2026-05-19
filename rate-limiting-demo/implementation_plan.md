# Session Resilience Demo - Implementation Plan

A Spring Boot project demonstrating how to prevent "login storms" after Redis session cache restart.

## Problem Scenario

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           THE LOGIN STORM PROBLEM                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  1. Normal Operation:                                                       │
│     └── 1M users with sessions cached in Redis ✓                           │
│                                                                             │
│  2. Redis Restarts:                                                         │
│     └── All sessions invalidated instantly ⚠                               │
│                                                                             │
│  3. Login Storm Begins:                                                     │
│     └── 1M users try to re-authenticate simultaneously                     │
│         └── Database overwhelmed with auth queries                         │
│         └── Servers crash from CPU/memory exhaustion                        │
│         └── Complete service outage! ✗                                      │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Solution Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                         MULTI-LAYER RESILIENCE                              │
├──────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  Layer 1: Hybrid Session Storage                                            │
│  ├── Primary: Redis (fast, in-memory)                                       │
│  └── Backup: PostgreSQL/H2 (persistent)                                     │
│                                                                              │
│  Layer 2: JWT with Redis Revocation Check                                   │
│  ├── JWT tokens valid without Redis                                         │
│  └── Redis only used for revocation/blacklist                               │
│                                                                              │
│  Layer 3: Rate Limiting                                                     │
│  ├── Per-IP rate limiting                                                   │
│  └── Global login rate throttling                                           │
│                                                                              │
│  Layer 4: Circuit Breaker                                                   │
│  ├── Redis connection circuit breaker                                       │
│  └── Fallback to database when Redis fails                                  │
│                                                                              │
│  Layer 5: Session Recovery                                                  │
│  ├── Warm Redis from database on restart                                    │
│  └── Background sync process                                                │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Proposed Changes

### Project Structure

#### [NEW] session-resilience-demo/

```
session-resilience-demo/
├── pom.xml
├── docker-compose.yml
├── README.md
├── docs/
│   └── ARCHITECTURE.md
└── src/main/java/org/codeart/session/
    ├── SessionResilienceApplication.java
    ├── config/
    │   ├── RedisConfig.java
    │   ├── SecurityConfig.java
    │   ├── RateLimitConfig.java
    │   └── ResilienceConfig.java
    ├── entity/
    │   ├── User.java
    │   └── SessionBackup.java
    ├── repository/
    │   ├── UserRepository.java
    │   └── SessionBackupRepository.java
    ├── service/
    │   ├── AuthService.java
    │   ├── SessionService.java
    │   ├── SessionRecoveryService.java
    │   └── JwtTokenProvider.java
    ├── filter/
    │   ├── JwtAuthenticationFilter.java
    │   └── RateLimitFilter.java
    ├── controller/
    │   ├── AuthController.java
    │   ├── DemoController.java
    │   └── AdminController.java
    ├── dto/
    │   ├── LoginRequest.java
    │   ├── LoginResponse.java
    │   └── SessionStats.java
    └── exception/
        ├── GlobalExceptionHandler.java
        └── RateLimitExceededException.java
```

---

### Core Components

#### [NEW] pom.xml

Dependencies:
- Spring Boot 3.2.x (Web, Security, Data JPA, Data Redis, Validation, Actuator)
- H2 Database (for development)
- JWT (jjwt 0.12.x)
- Resilience4j (circuit breaker, rate limiting)
- Lombok
- SpringDoc OpenAPI

---

#### [NEW] docker-compose.yml

Services:
- Redis with persistence (AOF enabled)
- Redis Insight (monitoring UI)

---

#### [NEW] SecurityConfig.java

- Stateless session management
- JWT-based authentication
- Public endpoints: `/api/auth/**`, `/api/demo/**`, `/swagger-ui/**`
- Protected endpoints: `/api/protected/**`, `/api/admin/**`

---

#### [NEW] SessionService.java

Key features:
1. **Dual-write**: Save session to both Redis AND database
2. **Read-through**: Try Redis first, fallback to database
3. **Circuit breaker**: Protect against Redis failures
4. **Graceful degradation**: Continue serving users if Redis is down

```java
// Pseudocode
public Session getSession(String sessionId) {
    return circuitBreaker.run(() -> {
        Session session = redisTemplate.get(sessionId);
        if (session == null) {
            session = sessionBackupRepository.findBySessionId(sessionId);
            if (session != null) {
                redisTemplate.set(sessionId, session); // Warm cache
            }
        }
        return session;
    }, fallback -> sessionBackupRepository.findBySessionId(sessionId));
}
```

---

#### [NEW] SessionRecoveryService.java

Features:
1. On startup: Warm Redis from database backup
2. Batch processing to avoid overwhelming Redis
3. Priority recovery for recently active sessions
4. Background sync for full recovery

---

#### [NEW] RateLimitFilter.java

Using Resilience4j rate limiting:
- **Per-IP limit**: 10 login attempts per minute
- **Global limit**: 1000 logins per second across all users
- **Sliding window** algorithm
- Returns 429 Too Many Requests when exceeded

---

### Demo & Simulation Endpoints

#### [NEW] DemoController.java

1. `POST /api/demo/simulate-login-storm` - Simulate multiple concurrent logins
2. `POST /api/demo/simulate-redis-failure` - Kill Redis connection temporarily
3. `GET /api/demo/session-stats` - View current session statistics
4. `POST /api/demo/trigger-recovery` - Manually trigger session recovery

---

## Verification Plan

### Automated Tests

#### Build & Run
```bash
cd d:\anitgravity\spring-boot-projects\session-resilience-demo
mvn clean install -DskipTests
mvn spring-boot:run
```

### Manual Verification

#### 1. Test Normal Login Flow
```bash
# Register a user
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123","email":"test@test.com"}'

# Login and get JWT token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"test123"}'

# Access protected endpoint with token
curl -X GET http://localhost:8080/api/protected/profile \
  -H "Authorization: Bearer <token>"
```

#### 2. Test Redis Failure Resilience
```bash
# Simulate Redis failure
curl -X POST http://localhost:8080/api/demo/simulate-redis-failure

# Try to access protected resource (should still work via DB fallback)
curl -X GET http://localhost:8080/api/protected/profile \
  -H "Authorization: Bearer <token>"
```

#### 3. Test Rate Limiting
```bash
# Run multiple login attempts quickly (should get 429 after limit)
for i in {1..15}; do
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"wrong"}'
  echo ""
done
```

#### 4. Test Session Recovery
```bash
# Check session stats
curl http://localhost:8080/api/demo/session-stats

# Stop Redis container
docker-compose stop redis

# Wait and restart Redis
docker-compose start redis

# Trigger session recovery
curl -X POST http://localhost:8080/api/demo/trigger-recovery

# Verify sessions recovered
curl http://localhost:8080/api/demo/session-stats
```

#### 5. Swagger UI
Navigate to: http://localhost:8080/swagger-ui.html

---

## Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| JWT + Session hybrid | JWT for stateless auth, session for tracking/revocation |
| Database backup | Ensures sessions survive Redis restart |
| Batch recovery | Prevents overwhelming Redis during warm-up |
| Circuit breaker on Redis | Graceful degradation when Redis fails |
| Per-IP rate limiting | Prevents individual bad actors |
| Global rate limiting | Prevents system overload from distributed attack |
