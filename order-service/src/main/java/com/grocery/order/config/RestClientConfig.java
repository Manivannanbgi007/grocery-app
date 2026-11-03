package com.grocery.order.config;

import com.grocery.order.client.CorrelationIdForwardingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * One HTTP client per service this one calls (synchronous REST, only when placing an order).
 *
 * Spring Boot gives every bean method a fresh RestClient.Builder that already has the timeouts
 * from application.yml (spring.http.clients.*): if another service hangs, this one must not hang too.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient cartRestClient(RestClient.Builder builder, ServiceUrlsProperties serviceUrls) {
        return build(builder, serviceUrls.cartService());
    }

    @Bean
    public RestClient customerRestClient(RestClient.Builder builder, ServiceUrlsProperties serviceUrls) {
        return build(builder, serviceUrls.customerService());
    }

    @Bean
    public RestClient storeRestClient(RestClient.Builder builder, ServiceUrlsProperties serviceUrls) {
        return build(builder, serviceUrls.storeService());
    }

    private RestClient build(RestClient.Builder builder, String baseUrl) {
        return builder.baseUrl(baseUrl).requestInterceptor(new CorrelationIdForwardingInterceptor()).build();
    }
}
