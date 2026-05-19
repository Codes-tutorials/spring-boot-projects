package org.codeart.ratelimit.aspect;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.codeart.ratelimit.annotation.RateLimit;
import org.codeart.ratelimit.dto.RateLimitInfo;
import org.codeart.ratelimit.exception.RateLimitExceededException;
import org.codeart.ratelimit.resolver.ApiKeyResolver;
import org.codeart.ratelimit.resolver.IpKeyResolver;
import org.codeart.ratelimit.resolver.KeyResolver;
import org.codeart.ratelimit.resolver.UserKeyResolver;
import org.codeart.ratelimit.service.RateLimitMetricsService;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AOP aspect to process @RateLimit annotations on methods.
 * Provides fine-grained control over rate limiting per endpoint.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final IpKeyResolver ipKeyResolver;
    private final ApiKeyResolver apiKeyResolver;
    private final UserKeyResolver userKeyResolver;
    private final RateLimitMetricsService metricsService;

    // Cache for annotation-specific buckets
    private final Map<String, Bucket> bucketCache = new ConcurrentHashMap<>();

    @Around("@annotation(rateLimit)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        if (request == null) {
            log.warn("No HTTP request context available for @RateLimit annotation");
            return joinPoint.proceed();
        }

        String key = resolveKey(request, rateLimit, joinPoint);
        Bucket bucket = resolveBucket(key, rateLimit);

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        String limiterName = rateLimit.name().isEmpty() ? joinPoint.getSignature().getName() : rateLimit.name();

        RateLimitInfo info = buildRateLimitInfo(probe, rateLimit);

        if (probe.isConsumed()) {
            log.debug("@RateLimit check passed for '{}', key: {}, remaining: {}",
                    limiterName, key, probe.getRemainingTokens());
            metricsService.recordRequest(limiterName, true);
            return joinPoint.proceed();
        } else {
            log.info("@RateLimit exceeded for '{}', key: {}", limiterName, key);
            metricsService.recordRequest(limiterName, false);
            throw new RateLimitExceededException(rateLimit.message(), key, info);
        }
    }

    private HttpServletRequest getCurrentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String resolveKey(HttpServletRequest request, RateLimit rateLimit, ProceedingJoinPoint joinPoint) {
        KeyResolver resolver = switch (rateLimit.keyType()) {
            case IP -> ipKeyResolver;
            case API_KEY -> apiKeyResolver;
            case USER -> userKeyResolver;
            case CUSTOM -> ipKeyResolver; // Fallback to IP
        };

        String baseKey = resolver.resolve(request);
        String methodKey = getMethodKey(joinPoint);

        String prefix = rateLimit.keyPrefix().isEmpty() ? "" : rateLimit.keyPrefix() + ":";
        return prefix + methodKey + ":" + baseKey;
    }

    private String getMethodKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    private Bucket resolveBucket(String key, RateLimit rateLimit) {
        return bucketCache.computeIfAbsent(key, k -> createBucket(rateLimit));
    }

    private Bucket createBucket(RateLimit rateLimit) {
        Duration duration = Duration.of(rateLimit.duration(), rateLimit.timeUnit().toChronoUnit());

        Bandwidth limit = Bandwidth.builder()
                .capacity(rateLimit.limit())
                .refillGreedy(rateLimit.limit(), duration)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private RateLimitInfo buildRateLimitInfo(ConsumptionProbe probe, RateLimit rateLimit) {
        long resetAtSeconds = Instant.now().getEpochSecond() +
                (probe.getNanosToWaitForRefill() / 1_000_000_000L);

        return RateLimitInfo.builder()
                .limit(rateLimit.limit())
                .remaining(probe.getRemainingTokens())
                .resetAt(resetAtSeconds)
                .retryAfterSeconds(probe.getNanosToWaitForRefill() / 1_000_000_000L)
                .tier("custom")
                .build();
    }
}
