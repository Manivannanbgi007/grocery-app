package com.grocery.delivery.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Swagger / OpenAPI documentation.
 *
 * - Own UI:        http://localhost:8101/swagger-ui.html
 * - Central UI:    http://localhost:8080/swagger-ui.html (API gateway, all services in one dropdown)
 *
 * The server URL is relative ("/"), so "Try it out" calls whichever host shows the page:
 * the service itself, or the API gateway.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    public OpenAPI deliveryServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Delivery Service API")
                        .version("v1")
                        .description("Delivery partners (KYC, online status, earnings), delivery zones, and the deliveries themselves."))
                .servers(List.of(new Server().url("/").description("Current host")))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }
}
