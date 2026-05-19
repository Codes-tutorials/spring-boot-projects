package org.codeart.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.cache.cache.MultiLevelCacheManager;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Cache configuration for multi-level caching with Caffeine (L1) and Redis
 * (L2).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CacheConfig {

    private final CacheProperties cacheProperties;

    /**
     * Primary cache manager combining L1 (Caffeine) and L2 (Redis).
     */
    @Bean
    @Primary
    public CacheManager cacheManager(CacheManager caffeineCacheManager,
            CacheManager redisCacheManager) {
        if (cacheProperties.getMultiLevel().isEnabled()) {
            log.info("Creating multi-level cache manager");
            return new MultiLevelCacheManager(caffeineCacheManager, redisCacheManager);
        } else {
            log.info("Multi-level caching disabled, using Caffeine only");
            return caffeineCacheManager;
        }
    }

    /**
     * L1 Cache Manager - Caffeine (local, fast).
     */
    @Bean
    public CacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();

        // Default Caffeine spec
        cacheManager.setCaffeine(Caffeine.from(cacheProperties.getCaffeine().getDefaultSpec()));

        // Allow dynamic cache creation
        cacheManager.setAllowNullValues(true);

        log.info("Caffeine L1 cache manager created with spec: {}",
                cacheProperties.getCaffeine().getDefaultSpec());

        return cacheManager;
    }

    /**
     * L2 Cache Manager - Redis (distributed).
     */
    @Bean
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory) {
        // Default configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(cacheProperties.getRedis().getDefaultTtl()))
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // Per-cache configurations with custom TTLs
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheProperties.getRedis().getTtls().forEach((cacheName, ttl) -> {
            cacheConfigs.put(cacheName, defaultConfig.entryTtl(Duration.ofSeconds(ttl)));
            log.debug("Redis cache '{}' configured with TTL: {}s", cacheName, ttl);
        });

        RedisCacheManager cacheManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .enableStatistics()
                .build();

        log.info("Redis L2 cache manager created with default TTL: {}s",
                cacheProperties.getRedis().getDefaultTtl());

        return cacheManager;
    }
}
