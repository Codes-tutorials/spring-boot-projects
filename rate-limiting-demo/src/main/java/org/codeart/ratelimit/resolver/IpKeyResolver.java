package org.codeart.ratelimit.resolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Resolves rate limit key based on client IP address.
 * Properly handles reverse proxy headers (X-Forwarded-For, X-Real-IP).
 */
@Slf4j
@Component
public class IpKeyResolver implements KeyResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String X_REAL_IP = "X-Real-IP";
    private static final String CF_CONNECTING_IP = "CF-Connecting-IP"; // Cloudflare
    private static final String TRUE_CLIENT_IP = "True-Client-IP"; // Akamai, Cloudflare Enterprise

    @Override
    public String resolve(HttpServletRequest request) {
        String ip = extractClientIp(request);
        log.debug("Resolved client IP: {}", ip);
        return "ip:" + ip;
    }

    @Override
    public KeyType getKeyType() {
        return KeyType.IP;
    }

    /**
     * Extract the real client IP, considering reverse proxies.
     */
    private String extractClientIp(HttpServletRequest request) {
        // Check common proxy headers in order of preference
        String ip = request.getHeader(CF_CONNECTING_IP);
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader(TRUE_CLIENT_IP);
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader(X_REAL_IP);
        if (isValidIp(ip)) {
            return ip;
        }

        ip = request.getHeader(X_FORWARDED_FOR);
        if (isValidIp(ip)) {
            // X-Forwarded-For can contain multiple IPs, take the first (original client)
            return ip.split(",")[0].trim();
        }

        // Fall back to remote address
        ip = request.getRemoteAddr();

        // Handle IPv6 localhost
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            return "127.0.0.1";
        }

        return ip;
    }

    private boolean isValidIp(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }
}
