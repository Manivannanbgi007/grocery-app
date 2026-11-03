package com.grocery.inventory.config;

import com.grocery.inventory.client.CorrelationIdForwardingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients for calling other services (synchronous REST).
 *
 * The RestClient.Builder comes from Spring Boot and already has the timeouts from application.yml
 * (spring.http.clients.connect-timeout / read-timeout): if the other service hangs, this one must not hang too.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient productReadRestClient(RestClient.Builder builder, ServiceUrlsProperties serviceUrls) {
        return builder
                .baseUrl(serviceUrls.productReadService())
                .requestInterceptor(new CorrelationIdForwardingInterceptor())
                .build();
    }
}
