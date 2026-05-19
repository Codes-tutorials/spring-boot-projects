package org.codeart.ratelimit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Rate limit information included in API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Rate limit status information")
public class RateLimitInfo {

    @Schema(description = "Maximum requests allowed in the time window", example = "100")
    private long limit;

    @Schema(description = "Remaining requests in current time window", example = "95")
    private long remaining;

    @Schema(description = "Unix timestamp (seconds) when the rate limit resets", example = "1705350000")
    private long resetAt;

    @Schema(description = "Seconds until the rate limit resets", example = "45")
    private long retryAfterSeconds;

    @Schema(description = "The tier this rate limit applies to", example = "basic")
    private String tier;
}
