package org.codeart.jwt.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT", description = "Enter JWT token")
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("JWT Authentication Demo API")
                        .version("1.0.0")
                        .description("Production-ready JWT authentication with Spring Security. " +
                                "Features: Access/Refresh tokens, BCrypt encryption, Role-based access.")
                        .contact(new Contact()
                                .name("CodeArt")
                                .url("https://github.com/codeart")));
    }
}
