package org.codeart.cache.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

/**
 * Driver location model for Uber-style location caching.
 * Must be fresh (TTL 5 seconds) for real-time tracking.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriverLocation implements Serializable {

    private static final long serialVersionUID = 1L;

    private String driverId;
    private String driverName;
    private double latitude;
    private double longitude;
    private double heading; // Direction in degrees
    private double speed; // km/h
    private String status; // AVAILABLE, ON_TRIP, OFFLINE
    private String vehicleType; // CAR, BIKE, AUTO
    private Instant timestamp; // When location was captured
    private Instant expiresAt; // When this data becomes stale
    private boolean isStale;

    /**
     * Check if location data is stale (older than 5 seconds).
     */
    public boolean isStale() {
        return timestamp != null &&
                Instant.now().minusSeconds(5).isAfter(timestamp);
    }

    /**
     * Calculate distance from another location (Haversine formula).
     */
    public double distanceFrom(double lat, double lon) {
        final int R = 6371; // Earth's radius in km
        double latDistance = Math.toRadians(lat - this.latitude);
        double lonDistance = Math.toRadians(lon - this.longitude);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(this.latitude))
                        * Math.cos(Math.toRadians(lat))
                        * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
