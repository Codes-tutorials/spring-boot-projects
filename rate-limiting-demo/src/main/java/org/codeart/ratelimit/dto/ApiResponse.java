package org.codeart.ratelimit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Generic API response wrapper with rate limit info.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {

    @Schema(description = "Response status", example = "success")
    private String status;

    @Schema(description = "Response message")
    private String message;

    @Schema(description = "Response data payload")
    private T data;

    @Schema(description = "Response timestamp")
    private Instant timestamp;

    @Schema(description = "Rate limit information")
    private RateLimitInfo rateLimit;

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .status("success")
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, String message, RateLimitInfo rateLimitInfo) {
        return ApiResponse.<T>builder()
                .status("success")
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .rateLimit(rateLimitInfo)
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .status("error")
                .message(message)
                .timestamp(Instant.now())
                .build();
    }

    public static <T> ApiResponse<T> error(String message, RateLimitInfo rateLimitInfo) {
        return ApiResponse.<T>builder()
                .status("error")
                .message(message)
                .timestamp(Instant.now())
                .rateLimit(rateLimitInfo)
                .build();
    }
}
