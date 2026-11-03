package com.grocery.product.config;

import com.grocery.product.client.CorrelationIdForwardingInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * One RestClient per called service. Timeouts: spring.http.clients in application.yml.
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient orderRestClient(RestClient.Builder builder, ServiceUrlsProperties serviceUrls) {
        return builder.baseUrl(serviceUrls.orderService()).requestInterceptor(new CorrelationIdForwardingInterceptor()).build();
    }
}
