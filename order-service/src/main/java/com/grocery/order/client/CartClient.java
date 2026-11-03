package com.grocery.order.client;

import com.grocery.order.client.dto.CheckoutSummary;
import com.grocery.order.dto.common.ApiResponse;
import com.grocery.order.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Asks cart-service for the customer's checkout summary (GET /internal/carts/{userId}/checkout).
 * Any failure (down, timeout, error) -> ExternalServiceException -> 503 for our caller.
 */
@Component
public class CartClient {

    private static final ParameterizedTypeReference<ApiResponse<CheckoutSummary>> SUMMARY = new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public CartClient(@Qualifier("cartRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public CheckoutSummary getCheckoutSummary(Long customerUserId) {
        try {
            ApiResponse<CheckoutSummary> response = restClient.get()
                    .uri("/internal/carts/{customerUserId}/checkout", customerUserId)
                    .retrieve()
                    .body(SUMMARY);
            if (response == null || response.data() == null) {
                throw new ExternalServiceException("The cart service returned no checkout summary", null);
            }
            return response.data();
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Your cart is not available right now. Please try again later.", ex);
        }
    }
}
