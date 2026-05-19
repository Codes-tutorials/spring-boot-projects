package org.codeart.cache.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for multi-level caching.
 */
@Data
@Component
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    private MultiLevel multiLevel = new MultiLevel();
    private Caffeine caffeine = new Caffeine();
    private Redis redis = new Redis();
    private WriteBehind writeBehind = new WriteBehind();

    @Data
    public static class MultiLevel {
        private boolean enabled = true;
    }

    @Data
    public static class Caffeine {
        /**
         * Default Caffeine spec for caches
         */
        private String defaultSpec = "maximumSize=1000,expireAfterWrite=60s";

        /**
         * Per-cache Caffeine specs
         */
        private Map<String, String> specs = new HashMap<>();

        public String getSpec(String cacheName) {
            return specs.getOrDefault(cacheName, defaultSpec);
        }
    }

    @Data
    public static class Redis {
        /**
         * Default TTL in seconds
         */
        private long defaultTtl = 300;

        /**
         * Per-cache TTLs in seconds
         */
        private Map<String, Long> ttls = new HashMap<>();

        public long getTtl(String cacheName) {
            return ttls.getOrDefault(cacheName, defaultTtl);
        }
    }

    @Data
    public static class WriteBehind {
        private boolean enabled = true;
        private int batchSize = 50;
        private long flushIntervalMs = 5000;
    }
}
