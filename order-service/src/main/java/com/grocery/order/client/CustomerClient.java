package com.grocery.order.client;

import com.grocery.order.client.dto.CustomerAddress;
import com.grocery.order.dto.common.ApiResponse;
import com.grocery.order.exception.ExternalServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Asks customer-service for one of the customer's addresses (GET /internal/customers/{userId}/addresses/{addressId}).
 * 404 -> Optional.empty(); any other failure -> ExternalServiceException -> 503.
 */
@Component
public class CustomerClient {

    private static final ParameterizedTypeReference<ApiResponse<CustomerAddress>> ADDRESS = new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public CustomerClient(@Qualifier("customerRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<CustomerAddress> findAddress(Long customerUserId, Long addressId) {
        try {
            ApiResponse<CustomerAddress> response = restClient.get()
                    .uri("/internal/customers/{userId}/addresses/{addressId}", customerUserId, addressId)
                    .retrieve()
                    .body(ADDRESS);
            return Optional.ofNullable(response).map(ApiResponse::data);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new ExternalServiceException("Your addresses are not available right now. Please try again later.", ex);
        }
    }
}
