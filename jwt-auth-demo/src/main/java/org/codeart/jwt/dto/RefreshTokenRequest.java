package org.codeart.jwt.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Refresh token request DTO.
 */
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
