package com.grocery.cart.client;

import com.grocery.cart.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The REST client against a fake product-read-service (MockRestServiceServer): no network, no Spring context.
 */
class ProductCatalogClientTest {

    private static final String BASE_URL = "http://product-read-service";

    private MockRestServiceServer productReadService;
    private ProductCatalogClient productCatalogClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        productReadService = MockRestServiceServer.bindTo(builder).build();
        productCatalogClient = new ProductCatalogClient(builder.build());
    }

    @Test
    void findProduct_readsPriceAndStock() {
        productReadService.expect(requestTo(BASE_URL + "/internal/products/1"))
                .andRespond(withSuccess("""
                        { "success": true, "data": { "id": 1, "storeId": 1, "name": "Tomato", "unit": "KG", "unitQuantity": 1,
                          "price": 40.00, "sellingPrice": 36.00, "taxPercentage": 0.00, "imageUrls": [], "status": "ACTIVE",
                          "availableQuantity": 120, "inStock": true, "lowStock": false, "brand": null } }
                        """, MediaType.APPLICATION_JSON));

        assertThat(productCatalogClient.findProduct(1L)).hasValueSatisfying(product -> {
            assertThat(product.sellingPrice()).isEqualByComparingTo("36.00");
            assertThat(product.availableQuantity()).isEqualTo(120);
            assertThat(product.isActive()).isTrue();
        });
    }

    @Test
    void findProduct_returnsEmpty_forAnUnknownProduct() {
        productReadService.expect(requestTo(BASE_URL + "/internal/products/999")).andRespond(withResourceNotFound());

        assertThat(productCatalogClient.findProduct(999L)).isEmpty();
    }

    @Test
    void findProducts_asksForAllIdsInOneCall_andReturnsThemById() {
        productReadService.expect(requestTo(BASE_URL + "/internal/products?ids=1&ids=6"))
                .andRespond(withSuccess("""
                        { "success": true, "data": [
                          { "id": 1, "storeId": 1, "name": "Tomato", "status": "ACTIVE", "availableQuantity": 120 },
                          { "id": 6, "storeId": 1, "name": "Milk", "status": "ACTIVE", "availableQuantity": 60 } ] }
                        """, MediaType.APPLICATION_JSON));

        assertThat(productCatalogClient.findProducts(List.of(1L, 6L))).containsOnlyKeys(1L, 6L);
        productReadService.verify();
    }

    @Test
    void failures_become503() {
        productReadService.expect(requestTo(BASE_URL + "/internal/products?ids=1")).andRespond(withServerError());

        assertThatThrownBy(() -> productCatalogClient.findProducts(List.of(1L))).isInstanceOf(ExternalServiceException.class);
    }
}
