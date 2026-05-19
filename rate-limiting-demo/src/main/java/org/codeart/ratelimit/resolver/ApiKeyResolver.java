package org.codeart.ratelimit.resolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.config.RateLimitProperties;
import org.springframework.stereotype.Component;

/**
 * Resolves rate limit key based on API key header.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyResolver implements KeyResolver {

    public static final String API_KEY_HEADER = "X-Api-Key";

    private final RateLimitProperties rateLimitProperties;

    @Override
    public String resolve(HttpServletRequest request) {
        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            log.debug("No API key provided, falling back to anonymous");
            return "anonymous";
        }

        // Validate API key exists in configuration
        if (!rateLimitProperties.isValidApiKey(apiKey)) {
            log.debug("Invalid API key provided: {}", maskApiKey(apiKey));
            return "invalid:" + maskApiKey(apiKey);
        }

        log.debug("Resolved API key: {}", maskApiKey(apiKey));
        return "apikey:" + apiKey;
    }

    @Override
    public KeyType getKeyType() {
        return KeyType.API_KEY;
    }

    /**
     * Mask API key for logging (show first 4 and last 4 characters)
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 8) {
            return "***";
        }
        return apiKey.substring(0, 4) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
