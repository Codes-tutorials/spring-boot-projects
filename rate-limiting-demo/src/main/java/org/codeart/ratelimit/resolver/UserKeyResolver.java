package org.codeart.ratelimit.resolver;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Resolves rate limit key based on authenticated user.
 */
@Slf4j
@Component
public class UserKeyResolver implements KeyResolver {

    @Override
    public String resolve(HttpServletRequest request) {
        Principal userPrincipal = request.getUserPrincipal();

        if (userPrincipal == null) {
            log.debug("No authenticated user, falling back to anonymous");
            return "anonymous";
        }

        String username = userPrincipal.getName();
        log.debug("Resolved user: {}", username);
        return "user:" + username;
    }

    @Override
    public KeyType getKeyType() {
        return KeyType.USER;
    }
}
