package org.codeart.ratelimit.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private int serverPort;

    @Bean
    public OpenAPI rateLimitOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rate Limiting Demo API")
                        .description("""
                                Production-ready rate limiting demonstration using:
                                - **Bucket4j** for token bucket algorithm (global/IP-based)
                                - **Resilience4j** for method-level rate limiting
                                - **Redis** for distributed rate limiting (optional)

                                ## Rate Limit Tiers
                                | Tier | Requests/Minute | Burst Capacity |
                                |------|-----------------|----------------|
                                | Free | 10 | 15 |
                                | Basic | 100 | 150 |
                                | Premium | 1000 | 1500 |

                                ## Rate Limit Headers
                                All responses include rate limit headers:
                                - `X-RateLimit-Limit`: Maximum requests allowed
                                - `X-RateLimit-Remaining`: Remaining requests in window
                                - `X-RateLimit-Reset`: Unix timestamp when limit resets
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CodeArt")
                                .url("https://github.com/codeart")
                                .email("contact@codeart.org"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server")));
    }
}
