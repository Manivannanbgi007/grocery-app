package com.grocery.payment.gateway.razorpay;

import com.grocery.payment.config.PaymentProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The RestClient for Razorpay's REST API. Razorpay uses HTTP Basic auth: key id as user, key secret as password.
 * The timeouts come from spring.http.clients in application.yml.
 */
@Configuration
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "razorpay")
public class RazorpayConfig {

    @Bean
    public RestClient razorpayRestClient(RestClient.Builder builder, PaymentProperties paymentProperties) {
        PaymentProperties.Razorpay razorpay = paymentProperties.razorpay();
        return builder
                .baseUrl(razorpay.baseUrl())
                .defaultHeaders(headers -> headers.setBasicAuth(razorpay.keyId(), razorpay.keySecret()))
                .build();
    }
}
