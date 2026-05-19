package org.codeart.ratelimit.exception;

import lombok.Getter;
import org.codeart.ratelimit.dto.RateLimitInfo;

/**
 * Exception thrown when rate limit is exceeded.
 */
@Getter
public class RateLimitExceededException extends RuntimeException {

    private final RateLimitInfo rateLimitInfo;
    private final String clientKey;

    public RateLimitExceededException(String message, String clientKey, RateLimitInfo rateLimitInfo) {
        super(message);
        this.clientKey = clientKey;
        this.rateLimitInfo = rateLimitInfo;
    }

    public RateLimitExceededException(String clientKey, RateLimitInfo rateLimitInfo) {
        this("Rate limit exceeded for key: " + clientKey, clientKey, rateLimitInfo);
    }
}
