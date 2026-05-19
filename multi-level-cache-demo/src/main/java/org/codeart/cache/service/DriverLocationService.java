package org.codeart.cache.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.model.DriverLocation;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Uber-style Driver Location Service
 * 
 * Implements strict TTL (5 seconds) caching:
 * - Location data MUST be fresh (5 sec max)
 * - Stale data is dangerous (wrong ETA, wrong driver position)
 * - High-frequency updates from drivers
 * 
 * Real-world considerations:
 * - Drivers update location every 1-3 seconds
 * - Riders need real-time tracking
 * - Stale locations = bad UX
 */
@Slf4j
@Service
public class DriverLocationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MeterRegistry meterRegistry;

    // Ultra-short TTL cache for real-time data
    private Cache<String, DriverLocation> locationCache;

    // Simulated "database" of driver locations
    private final Map<String, DriverLocation> driverDatabase = new ConcurrentHashMap<>();

    private static final String REDIS_PREFIX = "driver:location:";
    private static final int TTL_SECONDS = 5; // CRITICAL: Must be fresh!
    private static final String GEO_KEY = "drivers:geo";

    public DriverLocationService(RedisTemplate<String, Object> redisTemplate,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void init() {
        // Short TTL cache - location data expires quickly
        this.locationCache = Caffeine.newBuilder()
                .maximumSize(50000) // 50K drivers
                .expireAfterWrite(TTL_SECONDS, TimeUnit.SECONDS) // 5 sec TTL
                .recordStats()
                .build();

        // Register gauge for active drivers
        Gauge.builder("drivers.active.count", driverDatabase, Map::size)
                .description("Number of active drivers")
                .register(meterRegistry);

        // Seed some test drivers
        seedTestDrivers();

        log.info("Driver location cache initialized: TTL={}s (STRICT)", TTL_SECONDS);
    }

    /**
     * Get driver location - MUST be fresh (max 5 seconds old).
     */
    public Optional<DriverLocation> getDriverLocation(String driverId) {
        // Check L1 cache first
        DriverLocation cached = locationCache.getIfPresent(driverId);

        if (cached != null) {
            // CRITICAL: Check if data is stale even if in cache
            if (cached.isStale()) {
                log.warn("DRIVER {} location is STALE ({}s old), refreshing",
                        driverId,
                        Duration.between(cached.getTimestamp(), Instant.now()).getSeconds());
                locationCache.invalidate(driverId);
                meterRegistry.counter("driver.location.stale").increment();
                // Fall through to fetch fresh data
            } else {
                log.debug("DRIVER {} cache HIT (fresh)", driverId);
                meterRegistry.counter("driver.location.cache.hit").increment();
                return Optional.of(cached);
            }
        }

        // Try Redis
        try {
            Object redisValue = redisTemplate.opsForValue().get(REDIS_PREFIX + driverId);
            if (redisValue instanceof DriverLocation location) {
                if (!location.isStale()) {
                    log.debug("DRIVER {} Redis HIT (fresh)", driverId);
                    locationCache.put(driverId, location);
                    return Optional.of(location);
                }
            }
        } catch (Exception e) {
            log.warn("Redis read failed for driver:{}", driverId, e);
        }

        // Fetch from "database" (simulated real-time GPS feed)
        log.info("DRIVER {} fetching FRESH location", driverId);
        meterRegistry.counter("driver.location.cache.miss").increment();

        return Optional.ofNullable(driverDatabase.get(driverId));
    }

    /**
     * Update driver location (called every 1-3 seconds by driver app).
     */
    public DriverLocation updateLocation(String driverId, double latitude, double longitude,
            double heading, double speed, String status) {
        Instant now = Instant.now();

        DriverLocation location = DriverLocation.builder()
                .driverId(driverId)
                .driverName("Driver " + driverId)
                .latitude(latitude)
                .longitude(longitude)
                .heading(heading)
                .speed(speed)
                .status(status)
                .vehicleType("CAR")
                .timestamp(now)
                .expiresAt(now.plusSeconds(TTL_SECONDS))
                .build();

        // Update in-memory "database"
        driverDatabase.put(driverId, location);

        // Update L1 cache
        locationCache.put(driverId, location);

        // Update Redis with TTL
        try {
            redisTemplate.opsForValue().set(
                    REDIS_PREFIX + driverId,
                    location,
                    Duration.ofSeconds(TTL_SECONDS));

            // Also update geo index for nearby queries
            redisTemplate.opsForGeo().add(GEO_KEY,
                    new org.springframework.data.geo.Point(longitude, latitude),
                    driverId);
        } catch (Exception e) {
            log.warn("Redis write failed for driver:{}", driverId, e);
        }

        log.debug("DRIVER {} location updated: ({}, {}), speed={}km/h",
                driverId, latitude, longitude, speed);
        meterRegistry.counter("driver.location.update").increment();

        return location;
    }

    /**
     * Find nearby drivers within radius (uses Redis GEO).
     */
    public List<DriverLocation> findNearbyDrivers(double latitude, double longitude,
            double radiusKm, int limit) {
        log.info("Finding drivers within {}km of ({}, {})", radiusKm, latitude, longitude);

        List<DriverLocation> nearbyDrivers = new ArrayList<>();

        try {
            // Use Redis GEO for spatial query
            var results = redisTemplate.opsForGeo().radius(
                    GEO_KEY,
                    new org.springframework.data.geo.Circle(
                            new org.springframework.data.geo.Point(longitude, latitude),
                            new org.springframework.data.geo.Distance(radiusKm,
                                    org.springframework.data.redis.connection.RedisGeoCommands.DistanceUnit.KILOMETERS)));

            if (results != null && results.getContent() != null) {
                for (var result : results.getContent()) {
                    String driverId = String.valueOf(result.getContent().getName());
                    getDriverLocation(driverId).ifPresent(location -> {
                        // Only include AVAILABLE drivers with fresh location
                        if ("AVAILABLE".equals(location.getStatus()) && !location.isStale()) {
                            nearbyDrivers.add(location);
                        }
                    });
                    if (nearbyDrivers.size() >= limit)
                        break;
                }
            }
        } catch (Exception e) {
            log.warn("Redis GEO query failed, falling back to in-memory", e);

            // Fallback: calculate distances in memory
            nearbyDrivers.addAll(
                    driverDatabase.values().stream()
                            .filter(d -> "AVAILABLE".equals(d.getStatus()))
                            .filter(d -> !d.isStale())
                            .filter(d -> d.distanceFrom(latitude, longitude) <= radiusKm)
                            .sorted(Comparator.comparingDouble(d -> d.distanceFrom(latitude, longitude)))
                            .limit(limit)
                            .collect(Collectors.toList()));
        }

        log.info("Found {} nearby drivers", nearbyDrivers.size());
        meterRegistry.counter("driver.nearbySearch").increment();

        return nearbyDrivers;
    }

    /**
     * Get all active drivers.
     */
    public List<DriverLocation> getAllDrivers() {
        return new ArrayList<>(driverDatabase.values());
    }

    /**
     * Get cache statistics.
     */
    public Map<String, Object> getCacheStats() {
        var stats = locationCache.stats();
        long staleCount = driverDatabase.values().stream()
                .filter(DriverLocation::isStale)
                .count();

        return Map.of(
                "hitCount", stats.hitCount(),
                "missCount", stats.missCount(),
                "hitRate", String.format("%.2f%%", stats.hitRate() * 100),
                "activeDrivers", driverDatabase.size(),
                "staleLocations", staleCount,
                "ttlSeconds", TTL_SECONDS,
                "cacheSize", locationCache.estimatedSize());
    }

    /**
     * Seed test drivers for demo.
     */
    private void seedTestDrivers() {
        // Simulate drivers around a city center (e.g., Bangalore)
        double baseLat = 12.9716;
        double baseLon = 77.5946;
        Random random = new Random();

        for (int i = 1; i <= 20; i++) {
            String driverId = "driver-" + i;
            updateLocation(
                    driverId,
                    baseLat + (random.nextDouble() - 0.5) * 0.1,
                    baseLon + (random.nextDouble() - 0.5) * 0.1,
                    random.nextDouble() * 360,
                    random.nextDouble() * 60,
                    i % 3 == 0 ? "ON_TRIP" : "AVAILABLE");
        }
        log.info("Seeded 20 test drivers");
    }
}
