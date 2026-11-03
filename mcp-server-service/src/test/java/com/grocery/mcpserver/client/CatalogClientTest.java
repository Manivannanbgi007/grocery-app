package com.grocery.mcpserver.client;

import com.grocery.mcpserver.client.dto.ProductData;
import com.grocery.mcpserver.exception.ExternalServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.GET;

/** The gateway calls, against a fake server (no network, no Spring context). */
class CatalogClientTest {

    /** A real answer of GET /api/v1/products (fields the client does not use, like "sku", are skipped). */
    private static final String PAGE_WITH_BUTTER = """
            {"success": true, "message": "Products fetched successfully",
             "data": {"content": [{"id": 7, "storeId": 1, "categoryId": 3, "categoryName": "Dairy & Eggs", "name": "Amul Butter",
                                   "description": "Pasteurised salted butter", "brand": "Amul", "sku": "FM-DRY-002", "unit": "G",
                                   "unitQuantity": 100.000, "price": 56.00, "discountPercentage": 0.00, "sellingPrice": 56.00,
                                   "taxPercentage": 12.00, "imageUrls": [], "status": "ACTIVE", "availableQuantity": 3,
                                   "inStock": true, "lowStock": true, "ratingAverage": 4.5, "ratingCount": 2,
                                   "updatedAt": "2026-10-26T10:00:00Z"}],
                      "page": 0, "size": 5, "totalElements": 1, "totalPages": 1, "last": true},
             "errors": null, "timestamp": "2026-10-26T10:00:00Z", "path": null}""";

    private MockRestServiceServer gateway;
    private CatalogClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway");
        gateway = MockRestServiceServer.bindTo(builder).build();
        client = new CatalogClient(builder.build());
    }

    @Test
    void searchSendsTheFiltersAndEncodesTheSearchTextCompletely() {
        // "&" and spaces in the search text must not break the address
        gateway.expect(requestTo("http://gateway/api/v1/products?size=5&inStockOnly=true&q=salt%20%26%20pepper&categoryId=2&maxPrice=100.0"))
                .andExpect(method(GET))
                .andRespond(withSuccess(PAGE_WITH_BUTTER, MediaType.APPLICATION_JSON));

        var page = client.searchProducts("salt & pepper", 2L, new BigDecimal("100.0"), true, 5);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().name()).isEqualTo("Amul Butter");
        gateway.verify();
    }

    @Test
    void searchLeavesOutTheFiltersThatAreNotSet() {
        gateway.expect(requestTo("http://gateway/api/v1/products?size=5&inStockOnly=false&q=milk"))
                .andRespond(withSuccess(PAGE_WITH_BUTTER, MediaType.APPLICATION_JSON));

        client.searchProducts("milk", null, null, false, 5);

        gateway.verify();
    }

    @Test
    void anUnknownProductIsEmptyNotAnError() {
        gateway.expect(requestTo("http://gateway/api/v1/products/999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"success\": false, \"message\": \"Product not found with id: 999\"}"));

        assertThat(client.findProduct(999L)).isEmpty();
    }

    @Test
    void suggestionsAskTheSearchBoxsEndpoint() {
        gateway.expect(requestTo("http://gateway/api/v1/products/suggestions?q=orgnic%20carrot&size=10"))
                .andRespond(withSuccess("""
                        {"success": true, "message": "Suggestions fetched successfully",
                         "data": [{"id": 7, "storeId": 1, "name": "Amul Butter", "unit": "G", "unitQuantity": 100.000,
                                   "sellingPrice": 56.00, "availableQuantity": 3, "inStock": true, "lowStock": true,
                                   "ratingCount": 2}]}""", MediaType.APPLICATION_JSON));

        assertThat(client.suggestProducts("orgnic carrot", null, 10)).extracting(ProductData::name).containsExactly("Amul Butter");
        gateway.verify();
    }

    @Test
    void aFailingServiceBecomesExternalServiceException() {
        gateway.expect(requestTo("http://gateway/api/v1/categories")).andRespond(withServerError());

        assertThatThrownBy(() -> client.categories())
                .isInstanceOf(ExternalServiceException.class)
                .hasMessage("The product catalog is not available right now. Please try again later.");
    }

    @Test
    void anUnsuccessfulAnswerIsAlsoAFailure() {
        gateway.expect(requestTo("http://gateway/api/v1/categories"))
                .andRespond(withSuccess("{\"success\": false, \"message\": \"Something went wrong\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.categories()).isInstanceOf(ExternalServiceException.class);
    }
}
