package org.codeart.ratelimit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.config.RateLimitProperties;
import org.springframework.stereotype.Service;

/**
 * Service for API key management and tier resolution.
 * In production, this would integrate with a database or external service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    private final RateLimitProperties rateLimitProperties;

    /**
     * Get the tier name for an API key.
     * 
     * @param apiKey the API key
     * @return the tier name (e.g., "free", "basic", "premium") or "anonymous" if
     *         not found
     */
    public String getTierForApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "anonymous";
        }

        String tier = rateLimitProperties.getTierName(apiKey);
        log.debug("Resolved tier '{}' for API key", tier);
        return tier;
    }

    /**
     * Validate if an API key is registered and active.
     */
    public boolean isValidApiKey(String apiKey) {
        return rateLimitProperties.isValidApiKey(apiKey);
    }

    /**
     * Get rate limit configuration for a tier.
     */
    public RateLimitProperties.TierConfig getTierConfig(String tierName) {
        return rateLimitProperties.getTiers().get(tierName);
    }

    /**
     * Get information about an API key (for admin purposes).
     */
    public ApiKeyInfo getApiKeyInfo(String apiKey) {
        if (!isValidApiKey(apiKey)) {
            return null;
        }

        String tier = getTierForApiKey(apiKey);
        RateLimitProperties.TierConfig tierConfig = getTierConfig(tier);

        return new ApiKeyInfo(
                apiKey,
                tier,
                tierConfig != null ? tierConfig.getRequestsPerMinute() : rateLimitProperties.getDefaultLimit(),
                tierConfig != null ? tierConfig.getBurstCapacity() : rateLimitProperties.getDefaultLimit(),
                true);
    }

    /**
     * API key information record.
     */
    public record ApiKeyInfo(
            String apiKey,
            String tier,
            int requestsPerMinute,
            int burstCapacity,
            boolean active) {
    }
}
