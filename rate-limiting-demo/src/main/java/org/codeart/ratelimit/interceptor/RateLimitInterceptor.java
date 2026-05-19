package org.codeart.ratelimit.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.config.RateLimitProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Rate limit interceptor for Spring MVC.
 * Can be used alongside or instead of the filter for more control.
 * This interceptor mainly adds request attributes for downstream processing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    public static final String RATE_LIMIT_START_TIME = "rateLimitStartTime";
    public static final String RATE_LIMIT_KEY = "rateLimitKey";

    private final RateLimitProperties rateLimitProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!rateLimitProperties.isEnabled()) {
            return true;
        }

        // Store start time for latency measurement
        request.setAttribute(RATE_LIMIT_START_TIME, System.nanoTime());

        // Log the incoming request for debugging
        log.debug("Rate limit interceptor - preHandle for: {} {}",
                request.getMethod(), request.getRequestURI());

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
            Object handler, Exception ex) {
        Long startTime = (Long) request.getAttribute(RATE_LIMIT_START_TIME);
        if (startTime != null) {
            long duration = System.nanoTime() - startTime;
            log.debug("Request completed in {}ms, status: {}",
                    duration / 1_000_000, response.getStatus());
        }
    }
}
