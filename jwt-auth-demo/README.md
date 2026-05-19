# JWT Authentication Demo

Production-ready **JWT Authentication** with Spring Security 6.

## Features

| Feature | Description |
|---------|-------------|
| **Access Token** | Short-lived (15 min), used for API calls |
| **Refresh Token** | Long-lived (7 days), stored in DB |
| **BCrypt** | Password encryption |
| **Role-Based Access** | USER, ADMIN, MODERATOR roles |
| **Token Revocation** | Logout invalidates refresh tokens |

## Quick Start

```bash
cd jwt-auth-demo
mvn spring-boot:run

# Swagger UI
http://localhost:8080/swagger-ui.html
```

## API Endpoints

### Authentication (Public)
```bash
# Register
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"john","email":"john@example.com","password":"password123"}'

# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"john","password":"password123"}'

# Refresh Token
curl -X POST http://localhost:8080/api/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"your-refresh-token"}'
```

### Protected Endpoints
```bash
# Get Profile (requires token)
curl http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"

# Admin Dashboard (requires ADMIN role)
curl http://localhost:8080/api/admin/dashboard \
  -H "Authorization: Bearer YOUR_ACCESS_TOKEN"
```

## JWT Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                           JWT Flow                                  │
│                                                                     │
│  1. Login ──────────────▶ Validate Credentials                     │
│                                   │                                 │
│                                   ▼                                 │
│                          Generate Access Token (15 min)            │
│                          Generate Refresh Token (7 days)            │
│                                   │                                 │
│                                   ▼                                 │
│  2. API Call ────────────▶ Validate Access Token                   │
│     + Bearer Token              │                                   │
│                                 ▼                                   │
│                          Return Protected Resource                  │
│                                                                     │
│  3. Token Expired ───────▶ Use Refresh Token                       │
│                                   │                                 │
│                                   ▼                                 │
│                          Generate New Access Token                  │
└─────────────────────────────────────────────────────────────────────┘
```

## Key Files

| File | Purpose |
|------|---------|
| `JwtTokenProvider` | Generate/validate JWT tokens |
| `JwtAuthenticationFilter` | Extract and validate token from requests |
| `SecurityConfig` | Spring Security configuration |
| `AuthService` | Register, login, refresh, logout |
| `AuthController` | Authentication endpoints |
