package com.grocery.order.client;

import com.grocery.order.client.dto.StoreInfo;
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
 * Asks store-service for a store in any status (GET /internal/stores/{storeId}).
 * 404 -> Optional.empty(); any other failure -> ExternalServiceException -> 503.
 */
@Component
public class StoreClient {

    private static final ParameterizedTypeReference<ApiResponse<StoreInfo>> STORE = new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public StoreClient(@Qualifier("storeRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<StoreInfo> findStore(Long storeId) {
        try {
            ApiResponse<StoreInfo> response = restClient.get()
                    .uri("/internal/stores/{storeId}", storeId)
                    .retrieve()
                    .body(STORE);
            return Optional.ofNullable(response).map(ApiResponse::data);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new ExternalServiceException("The store is not available right now. Please try again later.", ex);
        }
    }
}
