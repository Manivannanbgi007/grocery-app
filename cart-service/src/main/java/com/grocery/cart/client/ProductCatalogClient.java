package com.grocery.cart.client;

import com.grocery.cart.client.dto.CatalogProduct;
import com.grocery.cart.dto.common.ApiResponse;
import com.grocery.cart.exception.ExternalServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Asks product-read-service for current product details (name, price, status, stock).
 *
 * - 404 from the other service -> Optional.empty() ("no such product")
 * - any other failure (down, timeout, 5xx) -> ExternalServiceException -> 503 for our caller
 */
@Component
@RequiredArgsConstructor
public class ProductCatalogClient {

    private static final ParameterizedTypeReference<ApiResponse<CatalogProduct>> ONE_PRODUCT =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<CatalogProduct>>> PRODUCT_LIST =
            new ParameterizedTypeReference<>() { };
    private static final String NOT_AVAILABLE = "The product catalog is not available right now. Please try again later.";

    private final RestClient productReadRestClient;

    /** GET /internal/products/{id} */
    public Optional<CatalogProduct> findProduct(Long productId) {
        try {
            ApiResponse<CatalogProduct> response = productReadRestClient.get()
                    .uri("/internal/products/{productId}", productId)
                    .retrieve()
                    .body(ONE_PRODUCT);
            return Optional.ofNullable(response).map(ApiResponse::data);
        } catch (HttpClientErrorException.NotFound ex) {
            return Optional.empty();
        } catch (RestClientException ex) {
            throw new ExternalServiceException(NOT_AVAILABLE, ex);
        }
    }

    /**
     * GET /internal/products?ids=1&ids=2 - several products in ONE call (used at checkout).
     * Products that do not exist any more are missing from the map.
     */
    public Map<Long, CatalogProduct> findProducts(List<Long> productIds) {
        try {
            ApiResponse<List<CatalogProduct>> response = productReadRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/internal/products").queryParam("ids", productIds).build())
                    .retrieve()
                    .body(PRODUCT_LIST);
            List<CatalogProduct> products = response == null || response.data() == null ? List.of() : response.data();
            return products.stream().collect(Collectors.toMap(CatalogProduct::id, Function.identity()));
        } catch (RestClientException ex) {
            throw new ExternalServiceException(NOT_AVAILABLE, ex);
        }
    }
}
