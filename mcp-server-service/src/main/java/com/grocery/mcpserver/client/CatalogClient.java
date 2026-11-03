package com.grocery.mcpserver.client;

import com.grocery.mcpserver.client.dto.CategoryData;
import com.grocery.mcpserver.client.dto.GatewayResponse;
import com.grocery.mcpserver.client.dto.PageData;
import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.client.dto.ReviewData;
import com.grocery.mcpserver.config.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The catalog through the API gateway (public endpoints of product-read-service and product-service):
 * product search, one product, its reviews, the categories.
 */
@Component
@RequiredArgsConstructor
public class CatalogClient {

    private static final String CATALOG = "The product catalog";

    private static final ParameterizedTypeReference<GatewayResponse<PageData<ProductData>>> PRODUCT_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<List<ProductData>>> PRODUCT_LIST = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<ProductData>> PRODUCT = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<PageData<ReviewData>>> REVIEW_PAGE = new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<GatewayResponse<List<CategoryData>>> CATEGORIES = new ParameterizedTypeReference<>() { };

    private final RestClient gatewayRestClient;

    /**
     * GET /api/v1/products with the filters that are set (best matches first).
     *
     * The values go in as {placeholders}: the RestClient then encodes them completely, so a search text
     * like "salt & pepper" cannot break the address.
     */
    public PageData<ProductData> searchProducts(String query, Long categoryId, BigDecimal maxPrice, boolean inStockOnly, int size) {
        Map<String, Object> values = new HashMap<>();
        return GatewayCall.dataOf(CATALOG, () -> gatewayRestClient.get()
                .uri(uri -> {
                    uri.path("/api/v1/products").queryParam("size", size).queryParam("inStockOnly", inStockOnly);
                    if (query != null) {
                        uri.queryParam("q", "{query}");
                        values.put("query", query);
                    }
                    if (categoryId != null) {
                        uri.queryParam("categoryId", "{categoryId}");
                        values.put("categoryId", categoryId);
                    }
                    if (maxPrice != null) {
                        uri.queryParam("maxPrice", "{maxPrice}");
                        values.put("maxPrice", maxPrice.toPlainString());
                    }
                    return uri.build(values);
                })
                .retrieve()
                .body(PRODUCT_PAGE));
    }

    /**
     * GET /api/v1/products/suggestions: the website's search-box suggestions, which also find names with a small typo
     * ("tomoto" -> Tomato, "bananna" -> Banana Robusta). searchProducts above finds only the words as they are written.
     *
     * @param size at most 10 (product-read-service's limit)
     */
    public List<ProductData> suggestProducts(String text, Long categoryId, int size) {
        Map<String, Object> values = new HashMap<>();
        values.put("text", text);
        return GatewayCall.dataOf(CATALOG, () -> gatewayRestClient.get()
                .uri(uri -> {
                    uri.path("/api/v1/products/suggestions").queryParam("q", "{text}").queryParam("size", size);
                    if (categoryId != null) {
                        uri.queryParam("categoryId", "{categoryId}");
                        values.put("categoryId", categoryId);
                    }
                    return uri.build(values);
                })
                .retrieve()
                .body(PRODUCT_LIST));
    }

    /** GET /api/v1/products/{id}: empty when the product does not exist or is not for sale (404). */
    public Optional<ProductData> findProduct(long productId) {
        return GatewayCall.optionalDataOf(CATALOG, () -> gatewayRestClient.get()
                .uri("/api/v1/products/{productId}", productId)
                .retrieve()
                .body(PRODUCT));
    }

    /** GET /api/v1/products/{id}/reviews: the newest visible reviews first. */
    public PageData<ReviewData> latestReviews(long productId, int size) {
        return GatewayCall.dataOf("The reviews", () -> gatewayRestClient.get()
                .uri("/api/v1/products/{productId}/reviews?page=0&size={size}", productId, size)
                .retrieve()
                .body(REVIEW_PAGE));
    }

    /** GET /api/v1/categories, in display order (cached for a minute: categories rarely change). */
    @Cacheable(CacheNames.CATEGORIES)
    public List<CategoryData> categories() {
        return GatewayCall.dataOf(CATALOG, () -> gatewayRestClient.get()
                .uri("/api/v1/categories")
                .retrieve()
                .body(CATEGORIES));
    }
}
