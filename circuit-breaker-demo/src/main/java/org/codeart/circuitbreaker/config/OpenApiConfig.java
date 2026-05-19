package org.codeart.circuitbreaker.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Circuit Breaker Demo API")
                        .version("1.0.0")
                        .description("Production-ready Circuit Breaker with Resilience4j. " +
                                "Demonstrates circuit breaker, retry, rate limiter, bulkhead, and time limiter patterns.")
                        .contact(new Contact()
                                .name("CodeArt")
                                .url("https://github.com/codeart")));
    }
}
