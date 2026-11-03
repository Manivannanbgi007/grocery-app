package com.grocery.mcpserver.config;

import com.grocery.mcpserver.client.CorrelationIdForwardingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client for the API gateway.
 *
 * The RestClient.Builder comes from Spring Boot and already has the timeouts from application.yml
 * (spring.http.clients.connect-timeout / read-timeout): if a service hangs, the tool answers "not available"
 * instead of hanging too.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient gatewayRestClient(RestClient.Builder builder, GatewayProperties gateway) {
        return builder
                .baseUrl(gateway.url())
                .requestInterceptor(new CorrelationIdForwardingInterceptor())
                .build();
    }
}
