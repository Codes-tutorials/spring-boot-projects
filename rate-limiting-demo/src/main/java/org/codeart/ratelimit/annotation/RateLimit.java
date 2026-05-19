package org.codeart.ratelimit.annotation;

import org.codeart.ratelimit.resolver.KeyResolver.KeyType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Annotation for method-level rate limiting.
 * Applied to controller methods to enforce specific rate limits.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * Maximum number of requests allowed in the time window.
     */
    int limit() default 60;

    /**
     * Time window duration.
     */
    int duration() default 1;

    /**
     * Time unit for the duration.
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;

    /**
     * Key type for rate limiting (IP, API_KEY, USER).
     */
    KeyType keyType() default KeyType.IP;

    /**
     * Custom key prefix (optional).
     * If specified, this prefix is added to the resolved key.
     */
    String keyPrefix() default "";

    /**
     * Name of this rate limiter (used for logging and metrics).
     */
    String name() default "";

    /**
     * Custom error message when rate limit is exceeded.
     */
    String message() default "Rate limit exceeded. Please try again later.";
}
