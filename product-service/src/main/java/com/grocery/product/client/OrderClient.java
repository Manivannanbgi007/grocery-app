package com.grocery.product.client;

import com.grocery.product.client.dto.OrderInfo;
import com.grocery.product.dto.common.ApiResponse;
import com.grocery.product.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Asks order-service about an order (GET /internal/orders/{id}): a review is only allowed for a product
 * the customer really received.
 */
@Component
public class OrderClient {

    private static final ParameterizedTypeReference<ApiResponse<OrderInfo>> ORDER = new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public OrderClient(@Qualifier("orderRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<OrderInfo> findOrder(Long orderId) {
        try {
            ApiResponse<OrderInfo> response = restClient.get()
                    .uri("/internal/orders/{orderId}", orderId)
                    .retrieve()
                    .body(ORDER);
            return Optional.ofNullable(response).map(ApiResponse::data);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Reviews cannot be checked right now. Please try again later.", ex);
        }
    }
}
