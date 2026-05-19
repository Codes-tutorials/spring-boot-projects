package org.codeart.jwt.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Public endpoints (no authentication required).
 */
@RestController
@RequestMapping("/api/public")
@Tag(name = "Public Endpoints", description = "No authentication required")
public class PublicController {

    @GetMapping("/health")
    @Operation(summary = "Health check")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "JWT Auth Demo is running"));
    }

    @GetMapping("/info")
    @Operation(summary = "API information")
    public ResponseEntity<Map<String, Object>> info() {
        return ResponseEntity.ok(Map.of(
                "name", "JWT Authentication Demo",
                "version", "1.0.0",
                "description", "Production-ready JWT authentication with Spring Security",
                "features", Map.of(
                        "accessToken", "15 minutes expiry",
                        "refreshToken", "7 days expiry",
                        "passwordEncryption", "BCrypt",
                        "roles", "USER, ADMIN, MODERATOR")));
    }
}
