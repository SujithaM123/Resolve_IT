package com.dtcc.intern.demo.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI metadata. Documentation only - it enforces nothing.
 *
 * No security scheme is declared, so the Swagger page has no Authorize button and
 * cannot send a JWT. That is deliberate: Swagger is a reference for the endpoint
 * shapes, not a client for this API. Callers - the frontend, curl, Postman - obtain
 * a token from POST /api/auth/login and send it themselves as
 * 'Authorization: Bearer <token>'.
 *
 * Enforcement lives entirely in SecurityConfig (URL rules) and JwtAuthenticationFilter
 * (token validation). Deleting this file would change the Swagger page and nothing else.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI resolveItOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ResolveIT API")
                        .version("1.0.0")
                        .description("Incident management backend. Authentication is JWT-based: "
                                + "clients obtain a token from POST /api/auth/login and send it as "
                                + "'Authorization: Bearer <token>'."));
    }
}
