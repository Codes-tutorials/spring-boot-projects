package org.codeart.cache.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.lang.Nullable;

import java.util.concurrent.Callable;

/**
 * Multi-level cache implementation combining L1 (Caffeine) and L2 (Redis).
 * Read: L1 → L2 → miss
 * Write: L1 + L2
 * Evict: L1 + L2
 */
@Slf4j
public class MultiLevelCache implements Cache {

    private final String name;
    private final Cache l1Cache; // Caffeine (local, fast)
    private final Cache l2Cache; // Redis (distributed)

    public MultiLevelCache(String name, Cache l1Cache, Cache l2Cache) {
        this.name = name;
        this.l1Cache = l1Cache;
        this.l2Cache = l2Cache;
        log.info("Created multi-level cache '{}' with L1={}, L2={}",
                name, l1Cache.getClass().getSimpleName(), l2Cache.getClass().getSimpleName());
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    @Override
    @Nullable
    public ValueWrapper get(Object key) {
        // Try L1 first (fast, local)
        ValueWrapper value = l1Cache.get(key);
        if (value != null) {
            log.debug("Cache '{}' L1 HIT for key: {}", name, key);
            return value;
        }

        // Try L2 (distributed)
        value = l2Cache.get(key);
        if (value != null) {
            log.debug("Cache '{}' L2 HIT for key: {}, promoting to L1", name, key);
            // Promote to L1 for future fast access
            l1Cache.put(key, value.get());
            return value;
        }

        log.debug("Cache '{}' MISS for key: {}", name, key);
        return null;
    }

    @Override
    @Nullable
    public <T> T get(Object key, @Nullable Class<T> type) {
        ValueWrapper wrapper = get(key);
        if (wrapper == null) {
            return null;
        }
        Object value = wrapper.get();
        if (value != null && type != null && !type.isInstance(value)) {
            throw new IllegalStateException(
                    "Cached value is not of required type [" + type.getName() + "]: " + value);
        }
        return (T) value;
    }

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            return (T) wrapper.get();
        }

        // Cache miss - load value
        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        log.debug("Cache '{}' PUT key: {}", name, key);
        // Write to both caches
        l1Cache.put(key, value);
        l2Cache.put(key, value);
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        ValueWrapper existing = get(key);
        if (existing != null) {
            return existing;
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        log.debug("Cache '{}' EVICT key: {}", name, key);
        // Evict from both caches
        l1Cache.evict(key);
        l2Cache.evict(key);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        boolean evicted = false;
        if (l1Cache.evictIfPresent(key)) {
            evicted = true;
        }
        if (l2Cache.evictIfPresent(key)) {
            evicted = true;
        }
        if (evicted) {
            log.debug("Cache '{}' EVICTED key: {}", name, key);
        }
        return evicted;
    }

    @Override
    public void clear() {
        log.info("Cache '{}' CLEAR", name);
        l1Cache.clear();
        l2Cache.clear();
    }

    @Override
    public boolean invalidate() {
        clear();
        return true;
    }

    /**
     * Get L1 cache for direct access (metrics, etc.)
     */
    public Cache getL1Cache() {
        return l1Cache;
    }

    /**
     * Get L2 cache for direct access
     */
    public Cache getL2Cache() {
        return l2Cache;
    }
}
