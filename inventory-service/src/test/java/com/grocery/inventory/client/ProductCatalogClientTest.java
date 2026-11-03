package com.grocery.inventory.client;

import com.grocery.inventory.client.dto.CatalogProduct;
import com.grocery.inventory.constant.HeaderNames;
import com.grocery.inventory.exception.ExternalServiceException;
import com.grocery.inventory.filter.CorrelationIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
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
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(BASE_URL)
                .requestInterceptor(new CorrelationIdForwardingInterceptor());
        productReadService = MockRestServiceServer.bindTo(builder).build();
        productCatalogClient = new ProductCatalogClient(builder.build());
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void findProduct_readsTheProductFromTheApiResponse_andIgnoresExtraFields() {
        productReadService.expect(requestTo(BASE_URL + "/internal/products/1"))
                .andRespond(withSuccess("""
                        { "success": true, "message": "Product fetched successfully",
                          "data": { "id": 1, "storeId": 1, "name": "Tomato", "status": "ACTIVE",
                                    "sellingPrice": 36.00, "imageUrls": [] } }
                        """, MediaType.APPLICATION_JSON));

        assertThat(productCatalogClient.findProduct(1L)).contains(new CatalogProduct(1L, 1L, "Tomato", "ACTIVE"));
        productReadService.verify();
    }

    @Test
    void findProduct_returnsEmpty_whenTheProductDoesNotExist() {
        productReadService.expect(requestTo(BASE_URL + "/internal/products/999")).andRespond(withResourceNotFound());

        assertThat(productCatalogClient.findProduct(999L)).isEmpty();
    }

    @Test
    void findProduct_throwsExternalServiceException_whenTheServiceFails() {
        productReadService.expect(requestTo(BASE_URL + "/internal/products/1")).andRespond(withServerError());

        assertThatThrownBy(() -> productCatalogClient.findProduct(1L)).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void findProduct_throwsExternalServiceException_onOtherClientErrors() {
        productReadService.expect(requestTo(BASE_URL + "/internal/products/1")).andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> productCatalogClient.findProduct(1L)).isInstanceOf(ExternalServiceException.class);
    }

    @Test
    void findProduct_forwardsTheCorrelationIdOfTheCurrentRequest() {
        MDC.put(CorrelationIdFilter.MDC_KEY, "test-correlation-id");
        productReadService.expect(requestTo(BASE_URL + "/internal/products/1"))
                .andExpect(header(HeaderNames.CORRELATION_ID, "test-correlation-id"))
                .andRespond(withResourceNotFound());

        productCatalogClient.findProduct(1L);

        productReadService.verify();
    }
}
