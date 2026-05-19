package org.codeart.cache.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Cache manager that creates multi-level caches combining L1 and L2 cache
 * managers.
 */
@Slf4j
public class MultiLevelCacheManager implements CacheManager {

    private final CacheManager l1CacheManager; // Caffeine
    private final CacheManager l2CacheManager; // Redis
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();
    private final boolean allowNullValues;

    public MultiLevelCacheManager(CacheManager l1CacheManager, CacheManager l2CacheManager) {
        this(l1CacheManager, l2CacheManager, true);
    }

    public MultiLevelCacheManager(CacheManager l1CacheManager, CacheManager l2CacheManager,
            boolean allowNullValues) {
        this.l1CacheManager = l1CacheManager;
        this.l2CacheManager = l2CacheManager;
        this.allowNullValues = allowNullValues;
        log.info("MultiLevelCacheManager created with L1={}, L2={}",
                l1CacheManager.getClass().getSimpleName(),
                l2CacheManager.getClass().getSimpleName());
    }

    @Override
    @Nullable
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, this::createMultiLevelCache);
    }

    private Cache createMultiLevelCache(String name) {
        Cache l1Cache = l1CacheManager.getCache(name);
        Cache l2Cache = l2CacheManager.getCache(name);

        if (l1Cache == null || l2Cache == null) {
            log.warn("Could not create multi-level cache '{}': L1={}, L2={}",
                    name, l1Cache, l2Cache);
            // Fallback to available cache
            return l1Cache != null ? l1Cache : l2Cache;
        }

        return new MultiLevelCache(name, l1Cache, l2Cache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheMap.keySet());
    }

    /**
     * Get the L1 (Caffeine) cache manager
     */
    public CacheManager getL1CacheManager() {
        return l1CacheManager;
    }

    /**
     * Get the L2 (Redis) cache manager
     */
    public CacheManager getL2CacheManager() {
        return l2CacheManager;
    }
}
