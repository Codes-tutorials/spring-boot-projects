package org.codeart.ratelimit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standardized error response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Error response")
public class ErrorResponse {

    @Schema(description = "Error type", example = "RATE_LIMIT_EXCEEDED")
    private String error;

    @Schema(description = "HTTP status code", example = "429")
    private int status;

    @Schema(description = "Human-readable error message")
    private String message;

    @Schema(description = "Error timestamp")
    private Instant timestamp;

    @Schema(description = "Request path that caused the error", example = "/api/v1/public")
    private String path;

    @Schema(description = "Rate limit information (for rate limit errors)")
    private RateLimitInfo rateLimit;

    public static ErrorResponse rateLimitExceeded(String message, String path, RateLimitInfo rateLimitInfo) {
        return ErrorResponse.builder()
                .error("RATE_LIMIT_EXCEEDED")
                .status(429)
                .message(message)
                .timestamp(Instant.now())
                .path(path)
                .rateLimit(rateLimitInfo)
                .build();
    }

    public static ErrorResponse badRequest(String message, String path) {
        return ErrorResponse.builder()
                .error("BAD_REQUEST")
                .status(400)
                .message(message)
                .timestamp(Instant.now())
                .path(path)
                .build();
    }
}
