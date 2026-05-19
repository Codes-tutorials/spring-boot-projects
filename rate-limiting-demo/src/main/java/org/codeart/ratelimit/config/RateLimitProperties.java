package org.codeart.ratelimit.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for rate limiting.
 * Supports tiered API key limits and default fallback limits.
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    /**
     * Enable or disable rate limiting globally
     */
    private boolean enabled = true;

    /**
     * Storage type: 'in-memory' or 'redis'
     */
    private String storageType = "in-memory";

    /**
     * Default requests allowed per duration if no tier matches
     */
    @NotNull
    private Integer defaultLimit = 60;

    /**
     * Default duration for rate limit window
     */
    @NotNull
    private Duration defaultDuration = Duration.ofMinutes(1);

    /**
     * Tier configurations for different API key levels
     */
    private Map<String, TierConfig> tiers = new HashMap<>();

    /**
     * API key to tier mapping
     */
    private Map<String, String> apiKeys = new HashMap<>();

    /**
     * Rate limit tier configuration
     */
    @Data
    public static class TierConfig {
        /**
         * Maximum requests allowed per minute
         */
        private int requestsPerMinute = 60;

        /**
         * Burst capacity (allows temporary spikes above normal rate)
         */
        private int burstCapacity = 60;

        /**
         * Get duration (always 1 minute for requests-per-minute)
         */
        public Duration getDuration() {
            return Duration.ofMinutes(1);
        }
    }

    /**
     * Get tier configuration for an API key
     */
    public TierConfig getTierForApiKey(String apiKey) {
        if (apiKey == null || !apiKeys.containsKey(apiKey)) {
            return getDefaultTierConfig();
        }
        String tierName = apiKeys.get(apiKey);
        return tiers.getOrDefault(tierName, getDefaultTierConfig());
    }

    /**
     * Get the tier name for an API key
     */
    public String getTierName(String apiKey) {
        if (apiKey == null || !apiKeys.containsKey(apiKey)) {
            return "anonymous";
        }
        return apiKeys.get(apiKey);
    }

    /**
     * Check if an API key is valid (registered)
     */
    public boolean isValidApiKey(String apiKey) {
        return apiKey != null && apiKeys.containsKey(apiKey);
    }

    private TierConfig getDefaultTierConfig() {
        TierConfig config = new TierConfig();
        config.setRequestsPerMinute(defaultLimit);
        config.setBurstCapacity(defaultLimit);
        return config;
    }
}
