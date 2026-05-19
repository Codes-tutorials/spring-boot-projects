package org.codeart.jwt.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.codeart.jwt.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Protected endpoints demonstrating JWT authorization.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Protected Endpoints", description = "Endpoints requiring JWT authentication")
public class ProtectedController {

    @GetMapping("/user/profile")
    @Operation(summary = "Get current user profile", description = "Requires authentication")
    public ResponseEntity<Map<String, Object>> getProfile(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail(),
                "firstName", user.getFirstName() != null ? user.getFirstName() : "",
                "lastName", user.getLastName() != null ? user.getLastName() : "",
                "roles", user.getRoles(),
                "createdAt", user.getCreatedAt().toString(),
                "lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : "N/A"));
    }

    @GetMapping("/user/dashboard")
    @Operation(summary = "User dashboard", description = "Any authenticated user")
    public ResponseEntity<Map<String, String>> userDashboard(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to the user dashboard, " + user.getUsername() + "!",
                "role", "USER"));
    }

    @GetMapping("/admin/dashboard")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin dashboard", description = "Requires ADMIN role")
    public ResponseEntity<Map<String, String>> adminDashboard(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to the admin dashboard, " + user.getUsername() + "!",
                "role", "ADMIN"));
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all users", description = "Admin only")
    public ResponseEntity<Map<String, String>> listUsers() {
        return ResponseEntity.ok(Map.of(
                "message", "This would return a list of all users",
                "note", "Admin only endpoint"));
    }

    @GetMapping("/moderator/content")
    @PreAuthorize("hasAnyRole('ADMIN', 'MODERATOR')")
    @Operation(summary = "Moderator content", description = "Requires ADMIN or MODERATOR role")
    public ResponseEntity<Map<String, String>> moderatorContent() {
        return ResponseEntity.ok(Map.of(
                "message", "Content for moderators",
                "role", "ADMIN or MODERATOR"));
    }
}
