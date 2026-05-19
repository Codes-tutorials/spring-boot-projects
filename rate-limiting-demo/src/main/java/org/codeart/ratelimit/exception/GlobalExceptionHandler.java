package org.codeart.ratelimit.exception;

import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.codeart.ratelimit.dto.ErrorResponse;
import org.codeart.ratelimit.dto.RateLimitInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

/**
 * Global exception handler for rate limiting errors.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle Resilience4j rate limit exceptions
     */
    @ExceptionHandler(RequestNotPermitted.class)
    public ResponseEntity<ErrorResponse> handleRateLimitException(
            RequestNotPermitted ex,
            HttpServletRequest request) {

        log.warn("Resilience4j rate limit exceeded for path: {}", request.getRequestURI());

        RateLimitInfo rateLimitInfo = RateLimitInfo.builder()
                .limit(0)
                .remaining(0)
                .resetAt(Instant.now().plusSeconds(60).getEpochSecond())
                .retryAfterSeconds(60)
                .tier("resilience4j")
                .build();

        ErrorResponse error = ErrorResponse.builder()
                .error("RATE_LIMIT_EXCEEDED")
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .message("Resilience4j rate limit exceeded. " + ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .rateLimit(rateLimitInfo)
                .build();

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "60")
                .body(error);
    }

    /**
     * Handle custom rate limit exceptions (from @RateLimit annotation)
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceededException(
            RateLimitExceededException ex,
            HttpServletRequest request) {

        log.warn("Custom rate limit exceeded for key: {}, path: {}",
                ex.getClientKey(), request.getRequestURI());

        ErrorResponse error = ErrorResponse.builder()
                .error("RATE_LIMIT_EXCEEDED")
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .message(ex.getMessage())
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .rateLimit(ex.getRateLimitInfo())
                .build();

        long retryAfter = ex.getRateLimitInfo() != null ? ex.getRateLimitInfo().getRetryAfterSeconds() : 60;

        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", String.valueOf(retryAfter))
                .header("X-RateLimit-Limit", String.valueOf(
                        ex.getRateLimitInfo() != null ? ex.getRateLimitInfo().getLimit() : 0))
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", String.valueOf(
                        ex.getRateLimitInfo() != null ? ex.getRateLimitInfo().getResetAt() : 0))
                .body(error);
    }

    /**
     * Handle generic exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unexpected error for path: {}", request.getRequestURI(), ex);

        ErrorResponse error = ErrorResponse.builder()
                .error("INTERNAL_SERVER_ERROR")
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .message("An unexpected error occurred")
                .timestamp(Instant.now())
                .path(request.getRequestURI())
                .build();

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error);
    }
}
