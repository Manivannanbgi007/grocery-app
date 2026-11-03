package com.grocery.cart;

import com.grocery.cart.client.ProductCatalogClient;
import com.grocery.cart.constant.HeaderNames;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.grocery.cart.support.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end inside the service (real MySQL, product-read-service replaced by a mock):
 * a vendor creates a coupon, a customer fills a cart, uses the coupon and reaches checkout.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")   // no Kafka needed here
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class CartFlowIntegrationTest {

    private static final String CUSTOMER = "900";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductCatalogClient productCatalogClient;

    @Test
    void vendorCoupon_customerCart_checkout() throws Exception {
        when(productCatalogClient.findProduct(1L)).thenReturn(Optional.of(product(1, 1, "Tomato", "36.00", 50)));
        when(productCatalogClient.findProduct(11L)).thenReturn(Optional.of(product(11, 2, "Organic Spinach", "30.00", 50)));
        when(productCatalogClient.findProducts(List.of(1L))).thenReturn(Map.of(1L, product(1, 1, "Tomato", "36.00", 50)));

        // 1. The vendor of store 1 creates a coupon (valid from yesterday for a month)
        Instant now = Instant.now();
        String created = mockMvc.perform(post("/api/v1/vendor/coupons").header(HeaderNames.USER_ID, "3").header(HeaderNames.STORE_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "flowtest15", "description": "15%% off at Fresh Mart", "discountType": "PERCENTAGE",
                                  "discountValue": 15, "maxDiscountAmount": 50, "minOrderAmount": 100,
                                  "validFrom": "%s", "validUntil": "%s" }
                                """.formatted(now.minus(1, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("FLOWTEST15"))
                .andReturn().getResponse().getContentAsString();
        Integer couponId = JsonPath.read(created, "$.data.id");

        // 2. Customers see it on the offers page of store 1
        mockMvc.perform(get("/api/v1/coupons").param("storeId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].code", hasItem("FLOWTEST15")));

        // 3. A customer adds 5 kg of tomatoes (180.00)
        mockMvc.perform(post("/api/v1/cart/items").header(HeaderNames.USER_ID, CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": 1, "quantity": 5 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeId").value(1))
                .andExpect(jsonPath("$.data.totals.itemsTotal").value(180.00));

        // 4. ...and cannot add a product of store 2 to the same cart
        mockMvc.perform(post("/api/v1/cart/items").header(HeaderNames.USER_ID, CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "productId": 11, "quantity": 1 }
                                """))
                .andExpect(status().isConflict());

        // 5. The coupon: 15 % of 180.00 = 27.00
        mockMvc.perform(put("/api/v1/cart/coupon").header(HeaderNames.USER_ID, CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "code": "FlowTest15" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.couponDiscount").value(27.00))
                .andExpect(jsonPath("$.data.totals.payableAmount").value(183.00));   // 180 - 27 + 30 delivery

        // 6. Checkout (what order-service will call): ready
        mockMvc.perform(get("/internal/carts/{customerUserId}/checkout", CUSTOMER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readyForCheckout").value(true))
                .andExpect(jsonPath("$.data.couponCode").value("FLOWTEST15"))
                .andExpect(jsonPath("$.data.totals.payableAmount").value(183.00));

        // 7. An admin switches the coupon off: the cart explains it, and checkout is blocked until it is removed
        mockMvc.perform(patch("/api/v1/admin/coupons/{id}/active", couponId).header(HeaderNames.USER_ID, "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "active": false }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cart").header(HeaderNames.USER_ID, CUSTOMER))
                .andExpect(jsonPath("$.data.couponMessage").value("Coupon FLOWTEST15 is not active"))
                .andExpect(jsonPath("$.data.totals.couponDiscount").value(0.00));
        mockMvc.perform(get("/api/v1/cart/checkout").header(HeaderNames.USER_ID, CUSTOMER))
                .andExpect(jsonPath("$.data.readyForCheckout").value(false))
                .andExpect(jsonPath("$.data.problems[0]", startsWith("Coupon FLOWTEST15 is not active")));
    }

    /** A slow product-read-service must not hold a database connection: it is called outside every transaction. */
    @Test
    void productReadServiceIsCalledOutsideAnyDatabaseTransaction() throws Exception {
        List<Boolean> transactionOpenDuringCall = new CopyOnWriteArrayList<>();
        when(productCatalogClient.findProduct(1L)).thenAnswer(call -> {
            transactionOpenDuringCall.add(TransactionSynchronizationManager.isActualTransactionActive());
            return Optional.of(product(1, 1, "Tomato", "36.00", 50));
        });
        when(productCatalogClient.findProducts(List.of(1L))).thenAnswer(call -> {
            transactionOpenDuringCall.add(TransactionSynchronizationManager.isActualTransactionActive());
            return Map.of(1L, product(1, 1, "Tomato", "36.00", 50));
        });
        String customer = "901";

        mockMvc.perform(post("/api/v1/cart/items").header(HeaderNames.USER_ID, customer)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "productId": 1, "quantity": 2 }
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/cart/items/1").header(HeaderNames.USER_ID, customer)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "quantity": 4 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totals.itemsTotal").value(144.00));
        mockMvc.perform(get("/internal/carts/{customerUserId}/checkout", customer))
                .andExpect(status().isOk());

        // add, more units, checkout: three calls, none of them inside a transaction
        assertThat(transactionOpenDuringCall).containsExactly(false, false, false);
    }
}
