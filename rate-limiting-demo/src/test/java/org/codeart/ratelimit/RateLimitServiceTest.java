package org.codeart.ratelimit;

import org.codeart.ratelimit.config.RateLimitProperties;
import org.codeart.ratelimit.dto.RateLimitInfo;
import org.codeart.ratelimit.service.RateLimitService;
import org.codeart.ratelimit.service.RateLimitService.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RateLimitService.
 */
@SpringBootTest
@ActiveProfiles("local")
class RateLimitServiceTest {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private RateLimitProperties rateLimitProperties;

    private static final String TEST_KEY = "test:service:unit";

    @BeforeEach
    void setUp() {
        // Reset the rate limit before each test
        rateLimitService.resetLimit(TEST_KEY);
    }

    @Test
    @DisplayName("Should allow requests within rate limit")
    void shouldAllowRequestsWithinLimit() {
        RateLimitResult result = rateLimitService.tryConsume(TEST_KEY, "free");

        assertTrue(result.allowed());
        assertNotNull(result.info());
        assertTrue(result.info().getRemaining() >= 0);
    }

    @Test
    @DisplayName("Should reject requests exceeding rate limit")
    void shouldRejectRequestsExceedingLimit() {
        String key = "test:exceed:" + System.currentTimeMillis();

        // Free tier has burst capacity of 15, so we need to exhaust that
        int burstCapacity = 15;

        // Exhaust the free tier burst capacity
        for (int i = 0; i < burstCapacity; i++) {
            RateLimitResult result = rateLimitService.tryConsume(key, "free");
            assertTrue(result.allowed(), "Request " + (i + 1) + " should be allowed");
        }

        // Next request should be denied (exceeded burst capacity)
        RateLimitResult result = rateLimitService.tryConsume(key, "free");
        assertFalse(result.allowed(), "Request after burst capacity should be denied");
        assertEquals(0, result.info().getRemaining());
    }

    @Test
    @DisplayName("Should return correct rate limit info")
    void shouldReturnCorrectRateLimitInfo() {
        String key = "test:info:" + System.currentTimeMillis();

        RateLimitResult result = rateLimitService.tryConsume(key, "basic");

        RateLimitInfo info = result.info();
        assertNotNull(info);
        assertEquals(100, info.getLimit()); // basic tier is 100/min
        assertEquals("basic", info.getTier());
        // After consuming 1 token, remaining should be less than burst capacity (150)
        assertTrue(info.getRemaining() < 150, "Remaining should be less than burst capacity");
    }

    @Test
    @DisplayName("Should get status without consuming tokens")
    void shouldGetStatusWithoutConsuming() {
        String key = "test:status:" + System.currentTimeMillis();

        // Consume one token first to initialize bucket
        rateLimitService.tryConsume(key, "free");

        // Get status
        RateLimitInfo status1 = rateLimitService.getStatus(key, "free");

        // Get status again - should be the same (no consumption)
        RateLimitInfo status2 = rateLimitService.getStatus(key, "free");

        assertEquals(status1.getRemaining(), status2.getRemaining());
    }

    @Test
    @DisplayName("Should handle different tiers correctly")
    void shouldHandleDifferentTiers() {
        String freeKey = "test:tier:free:" + System.currentTimeMillis();
        String premiumKey = "test:tier:premium:" + System.currentTimeMillis();

        RateLimitResult freeResult = rateLimitService.tryConsume(freeKey, "free");
        RateLimitResult premiumResult = rateLimitService.tryConsume(premiumKey, "premium");

        assertEquals(10, freeResult.info().getLimit()); // free: 10/min
        assertEquals(1000, premiumResult.info().getLimit()); // premium: 1000/min
    }

    @Test
    @DisplayName("Should work when rate limiting is disabled")
    void shouldWorkWhenDisabled() {
        boolean originalEnabled = rateLimitProperties.isEnabled();
        try {
            rateLimitProperties.setEnabled(false);

            RateLimitResult result = rateLimitService.tryConsume("disabled:test", "free");

            assertTrue(result.allowed());
            assertEquals(-1, result.info().getLimit()); // unlimited
        } finally {
            rateLimitProperties.setEnabled(originalEnabled);
        }
    }
}
