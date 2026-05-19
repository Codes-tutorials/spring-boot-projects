package org.codeart.cache.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.DriverLocation;
import org.codeart.cache.service.DriverLocationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Uber-style Driver Location Controller
 * Demonstrates strict TTL (5 sec) caching for real-time location tracking.
 */
@Slf4j
@RestController
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Tag(name = "Driver Location (Uber)", description = "Real-time driver location with strict 5-second TTL")
public class DriverLocationController {

    private final DriverLocationService driverLocationService;

    @GetMapping("/{driverId}/location")
    @Operation(summary = "Get driver location", description = "Returns driver location. Data must be fresh (max 5 seconds old). "
            +
            "Stale data is automatically refreshed.")
    public ResponseEntity<DriverLocation> getDriverLocation(@PathVariable String driverId) {
        return driverLocationService.getDriverLocation(driverId)
                .map(location -> {
                    log.info("Driver {} location: ({}, {}), stale={}",
                            driverId, location.getLatitude(), location.getLongitude(),
                            location.isStale());
                    return ResponseEntity.ok(location);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{driverId}/location")
    @Operation(summary = "Update driver location", description = "Called by driver app every 1-3 seconds to update position")
    public ResponseEntity<DriverLocation> updateLocation(
            @PathVariable String driverId,
            @RequestBody LocationUpdateRequest request) {

        DriverLocation location = driverLocationService.updateLocation(
                driverId,
                request.latitude(),
                request.longitude(),
                request.heading(),
                request.speed(),
                request.status());

        return ResponseEntity.ok(location);
    }

    @GetMapping("/nearby")
    @Operation(summary = "Find nearby drivers", description = "Find available drivers within radius using Redis GEO. " +
            "Only returns drivers with fresh location data.")
    public ResponseEntity<List<DriverLocation>> findNearbyDrivers(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam(defaultValue = "5") double radiusKm,
            @RequestParam(defaultValue = "10") int limit) {

        List<DriverLocation> drivers = driverLocationService.findNearbyDrivers(
                latitude, longitude, radiusKm, limit);

        return ResponseEntity.ok(drivers);
    }

    @GetMapping
    @Operation(summary = "Get all drivers")
    public ResponseEntity<List<DriverLocation>> getAllDrivers() {
        return ResponseEntity.ok(driverLocationService.getAllDrivers());
    }

    @GetMapping("/stats")
    @Operation(summary = "Get location cache statistics")
    public ResponseEntity<Map<String, Object>> getCacheStats() {
        return ResponseEntity.ok(driverLocationService.getCacheStats());
    }

    /**
     * Demo endpoint showing real-time behavior.
     */
    @GetMapping("/demo/stale")
    @Operation(summary = "Demo stale detection", description = "Shows how location data becomes stale after 5 seconds")
    public ResponseEntity<Map<String, Object>> demoStale() {
        String driverId = "driver-1";

        var locationOpt = driverLocationService.getDriverLocation(driverId);

        if (locationOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of("error", "Driver not found"));
        }

        DriverLocation location = locationOpt.get();
        var ageSeconds = java.time.Duration.between(
                location.getTimestamp(),
                java.time.Instant.now()).getSeconds();

        return ResponseEntity.ok(Map.of(
                "driverId", driverId,
                "latitude", location.getLatitude(),
                "longitude", location.getLongitude(),
                "ageSeconds", ageSeconds,
                "isStale", location.isStale(),
                "ttlSeconds", 5,
                "strategy", "Strict TTL (must be fresh)",
                "warning", location.isStale() ? "⚠️ DATA IS STALE - DO NOT USE FOR TRACKING" : "✅ Data is fresh"));
    }

    /**
     * Simulate driver movement for demo.
     */
    @PostMapping("/demo/simulate")
    @Operation(summary = "Simulate driver movement")
    public ResponseEntity<Map<String, String>> simulateMovement() {
        // Update all drivers with new positions
        for (int i = 1; i <= 5; i++) {
            String driverId = "driver-" + i;
            driverLocationService.updateLocation(
                    driverId,
                    12.9716 + (Math.random() - 0.5) * 0.05,
                    77.5946 + (Math.random() - 0.5) * 0.05,
                    Math.random() * 360,
                    30 + Math.random() * 30,
                    "AVAILABLE");
        }

        return ResponseEntity.ok(Map.of(
                "status", "simulated",
                "driversUpdated", "5"));
    }

    public record LocationUpdateRequest(
            double latitude,
            double longitude,
            double heading,
            double speed,
            String status) {
    }
}
