package com.grocery.mcpserver.client;

import com.grocery.mcpserver.exception.LoginRequiredException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The customer's own data: every call carries the customer's token; an expired login is not "service down". */
class CustomerClientTest {

    /** A real answer of GET /api/v1/cart (unused fields such as "imageUrl" are skipped). */
    private static final String CART = """
            {"success": true, "message": "Cart fetched", "data": {"customerUserId": 4, "storeId": 1,
             "items": [{"productId": 7, "productName": "Amul Butter", "unit": "G", "unitQuantity": 100.000, "imageUrl": null,
                        "price": 56.00, "sellingPrice": 56.00, "taxPercentage": 12.00, "quantity": 2, "lineTotal": 112.00}],
             "itemCount": 2, "couponCode": null, "couponMessage": null,
             "totals": {"itemsTotal": 112.00, "productSavings": 0.00, "couponDiscount": 0.00, "deliveryFee": 30.00,
                        "payableAmount": 142.00, "taxIncluded": 12.00, "minimumOrderValue": 99.00, "minimumOrderMet": true}},
             "errors": null, "timestamp": "2026-10-26T10:00:00Z", "path": null}""";

    private MockRestServiceServer gateway;
    private CustomerClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://gateway");
        gateway = MockRestServiceServer.bindTo(builder).build();
        client = new CustomerClient(builder.build());
    }

    @Test
    void theCartIsReadWithTheCustomersToken() {
        gateway.expect(requestTo("http://gateway/api/v1/cart"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer customer-jwt"))
                .andRespond(withSuccess(CART, MediaType.APPLICATION_JSON));

        var cart = client.cart("customer-jwt");

        assertThat(cart.items()).singleElement().satisfies(item -> assertThat(item.productName()).isEqualTo("Amul Butter"));
        assertThat(cart.totals().payableAmount()).isEqualByComparingTo("142");
        gateway.verify();
    }

    @Test
    void anExpiredLoginNeedsANewLogin() {
        gateway.expect(requestTo("http://gateway/api/v1/orders?page=0&size=5")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.latestOrders("expired-jwt", 5)).isInstanceOf(LoginRequiredException.class);
    }

    @Test
    void anOrderOfSomebodyElseIsNotFound() {
        // order-service answers 404 for orders of other customers
        gateway.expect(requestTo("http://gateway/api/v1/orders/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(client.order("customer-jwt", 99)).isEmpty();
    }
}
