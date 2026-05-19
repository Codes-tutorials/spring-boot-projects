package org.codeart.ratelimit.resolver;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface for resolving rate limit keys from requests.
 */
public interface KeyResolver {

    /**
     * Resolve the rate limit key from the request.
     * 
     * @param request the HTTP request
     * @return the resolved key (e.g., IP address, API key, user ID)
     */
    String resolve(HttpServletRequest request);

    /**
     * Get the type of key this resolver produces.
     */
    KeyType getKeyType();

    /**
     * Key types for rate limiting
     */
    enum KeyType {
        IP,
        API_KEY,
        USER,
        CUSTOM
    }
}
