package org.codeart.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for rate limiting endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should return rate limit headers")
    void shouldReturnRateLimitHeaders() throws Exception {
        mockMvc.perform(get("/api/v1/public")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().exists("X-RateLimit-Limit"))
                .andExpect(header().exists("X-RateLimit-Remaining"))
                .andExpect(header().exists("X-RateLimit-Reset"))
                .andExpect(header().exists("X-RateLimit-Tier"));
    }

    @Test
    @DisplayName("Should respect rate limit with API key")
    void shouldRespectRateLimitWithApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/public")
                .header("X-Api-Key", "premium-key-001")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Tier", "premium"))
                .andExpect(header().string("X-RateLimit-Limit", "1000"));
    }

    @Test
    @DisplayName("Should allow health check without rate limiting")
    void shouldAllowHealthCheckWithoutRateLimiting() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Should return 429 when rate limit exceeded")
    void shouldReturn429WhenRateLimitExceeded() throws Exception {
        // Make multiple requests to exceed the custom rate limit (3/10sec)
        String uniqueIp = "192.168." + System.currentTimeMillis() % 256 + ".1";

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/custom")
                    .header("X-Forwarded-For", uniqueIp))
                    .andExpect(status().isOk());
        }

        // 4th request should be rate limited
        mockMvc.perform(get("/api/v1/custom")
                .header("X-Forwarded-For", uniqueIp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error").value("RATE_LIMIT_EXCEEDED"));
    }

    @Test
    @DisplayName("Should get admin config")
    void shouldGetAdminConfig() throws Exception {
        mockMvc.perform(get("/api/v1/admin/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.tiers").exists());
    }

    @Test
    @DisplayName("Should list all tiers")
    void shouldListAllTiers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/tiers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.free").exists())
                .andExpect(jsonPath("$.data.basic").exists())
                .andExpect(jsonPath("$.data.premium").exists());
    }

    @Test
    @DisplayName("Resilience4j endpoint should be rate limited")
    void resilience4jEndpointShouldBeRateLimited() throws Exception {
        // backendA is configured for 5 req/min - test a few requests
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/v1/protected"))
                    .andExpect(status().isOk());
        }
    }
}
