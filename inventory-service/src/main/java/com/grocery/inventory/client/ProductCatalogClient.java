package com.grocery.inventory.client;

import com.grocery.inventory.client.dto.CatalogProduct;
import com.grocery.inventory.dto.common.ApiResponse;
import com.grocery.inventory.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Optional;

/**
 * Asks product-read-service about products (GET /internal/products/{id}).
 *
 * - 404 from the other service -> Optional.empty() ("no such product")
 * - any other failure (down, timeout, 5xx) -> ExternalServiceException -> 503 for our caller
 */
@Component
@RequiredArgsConstructor
public class ProductCatalogClient {

    private static final ParameterizedTypeReference<ApiResponse<CatalogProduct>> PRODUCT_RESPONSE =
            new ParameterizedTypeReference<>() { };

    private final RestClient productReadRestClient;

    public Optional<CatalogProduct> findProduct(Long productId) {
        try {
            ApiResponse<CatalogProduct> response = productReadRestClient.get()
                    .uri("/internal/products/{productId}", productId)
                    .retrieve()
                    .body(PRODUCT_RESPONSE);
            return Optional.ofNullable(response).map(ApiResponse::data);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new ExternalServiceException("The product catalog is not available right now. Please try again later.", ex);
        }
    }
}
