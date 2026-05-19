package org.codeart.cache.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.cache.CacheEventListener;
import org.codeart.cache.cache.MultiLevelCache;
import org.codeart.cache.cache.MultiLevelCacheManager;
import org.codeart.cache.config.CacheProperties;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Admin controller for cache management and monitoring.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/cache")
@RequiredArgsConstructor
@Tag(name = "Cache Admin", description = "Cache management and monitoring")
public class CacheAdminController {

    private final CacheManager cacheManager;
    private final CacheProperties cacheProperties;
    private final CacheEventListener cacheEventListener;

    @GetMapping("/config")
    @Operation(summary = "Get cache configuration")
    public ResponseEntity<CacheProperties> getConfig() {
        return ResponseEntity.ok(cacheProperties);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get cache statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("multiLevelEnabled", cacheProperties.getMultiLevel().isEnabled());
        stats.put("cacheNames", cacheManager.getCacheNames());

        if (cacheManager instanceof MultiLevelCacheManager mlcm) {
            stats.put("l1Manager", mlcm.getL1CacheManager().getClass().getSimpleName());
            stats.put("l2Manager", mlcm.getL2CacheManager().getClass().getSimpleName());
        }

        // Per-cache info
        Map<String, Map<String, Object>> cacheInfo = new HashMap<>();
        for (String cacheName : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                Map<String, Object> info = new HashMap<>();
                info.put("type", cache.getClass().getSimpleName());
                if (cache instanceof MultiLevelCache mlc) {
                    info.put("l1Type", mlc.getL1Cache().getClass().getSimpleName());
                    info.put("l2Type", mlc.getL2Cache().getClass().getSimpleName());
                }
                cacheInfo.put(cacheName, info);
            }
        }
        stats.put("caches", cacheInfo);

        return ResponseEntity.ok(stats);
    }

    @PostMapping("/evict/{cacheName}/{key}")
    @Operation(summary = "Evict specific key from cache")
    public ResponseEntity<Map<String, String>> evict(
            @PathVariable String cacheName,
            @PathVariable String key) {
        var cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        cache.evict(key);

        // Publish to all instances
        cacheEventListener.publishInvalidation(cacheName, key);

        log.info("Evicted key '{}' from cache '{}'", key, cacheName);
        return ResponseEntity.ok(Map.of(
                "status", "evicted",
                "cache", cacheName,
                "key", key));
    }

    @PostMapping("/clear/{cacheName}")
    @Operation(summary = "Clear all entries from a cache")
    public ResponseEntity<Map<String, String>> clear(@PathVariable String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        cache.clear();

        // Publish to all instances
        cacheEventListener.publishClearAll(cacheName);

        log.info("Cleared cache '{}'", cacheName);
        return ResponseEntity.ok(Map.of(
                "status", "cleared",
                "cache", cacheName));
    }

    @PostMapping("/clear-all")
    @Operation(summary = "Clear all caches")
    public ResponseEntity<Map<String, Object>> clearAll() {
        int count = 0;
        for (String cacheName : cacheManager.getCacheNames()) {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
                cacheEventListener.publishClearAll(cacheName);
                count++;
            }
        }

        log.info("Cleared {} caches", count);
        return ResponseEntity.ok(Map.of(
                "status", "cleared",
                "count", count));
    }

    @GetMapping("/keys/{cacheName}")
    @Operation(summary = "Get cache info (note: key listing not available for all cache types)")
    public ResponseEntity<Map<String, Object>> getCacheKeys(@PathVariable String cacheName) {
        var cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> info = new HashMap<>();
        info.put("name", cacheName);
        info.put("type", cache.getClass().getSimpleName());
        info.put("nativeCache", cache.getNativeCache().getClass().getSimpleName());

        return ResponseEntity.ok(info);
    }
}
