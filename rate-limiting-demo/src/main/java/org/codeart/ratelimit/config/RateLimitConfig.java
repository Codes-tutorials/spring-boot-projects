package org.codeart.ratelimit.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Configuration for Redis-backed distributed rate limiting using Bucket4j.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RateLimitConfig {

    private final RateLimitProperties rateLimitProperties;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    /**
     * Redis-backed ProxyManager for distributed rate limiting.
     * Only created when storage-type is 'redis'.
     */
    @Bean
    @ConditionalOnProperty(name = "rate-limit.storage-type", havingValue = "redis")
    public ProxyManager<String> redisProxyManager() {
        log.info("Initializing Redis-backed rate limit storage at {}:{}", redisHost, redisPort);

        RedisURI.Builder uriBuilder = RedisURI.builder()
                .withHost(redisHost)
                .withPort(redisPort);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            uriBuilder.withPassword(redisPassword.toCharArray());
        }

        RedisClient redisClient = RedisClient.create(uriBuilder.build());
        StatefulRedisConnection<String, byte[]> connection = redisClient.connect(
                RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE));

        return LettuceBasedProxyManager.builderFor(connection)
                .withExpirationStrategy(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(5)))
                .build();
    }

    /**
     * Create bucket configuration for a specific tier.
     */
    public BucketConfiguration createBucketConfiguration(String tierName) {
        RateLimitProperties.TierConfig tierConfig = rateLimitProperties.getTiers()
                .getOrDefault(tierName, createDefaultTierConfig());

        Bandwidth limit = Bandwidth.builder()
                .capacity(tierConfig.getBurstCapacity())
                .refillGreedy(tierConfig.getRequestsPerMinute(), tierConfig.getDuration())
                .build();

        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }

    /**
     * Supplier for bucket configuration - used by ProxyManager
     */
    public Supplier<BucketConfiguration> bucketConfigurationSupplier(String tierName) {
        return () -> createBucketConfiguration(tierName);
    }

    private RateLimitProperties.TierConfig createDefaultTierConfig() {
        RateLimitProperties.TierConfig config = new RateLimitProperties.TierConfig();
        config.setRequestsPerMinute(rateLimitProperties.getDefaultLimit());
        config.setBurstCapacity(rateLimitProperties.getDefaultLimit());
        return config;
    }
}
