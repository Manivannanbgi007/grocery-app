package com.grocery.product;

import com.grocery.product.client.OrderClient;
import com.grocery.product.client.dto.OrderInfo;
import com.grocery.product.constant.HeaderNames;
import com.grocery.product.entity.Product;
import com.grocery.product.repository.ProductRepository;
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
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reviews on a real MySQL (Testcontainers); order-service is replaced by a mocked OrderClient.
 * Proves: only received products can be reviewed, once per order, and the product's rating follows
 * the visible reviews (also after an admin hides one).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReviewFlowIntegrationTest {

    private static final long CUSTOMER = 4L;
    private static final long STORE = 8L;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository productRepository;
    @MockitoBean
    private OrderClient orderClient;

    @Test
    void customersReviewWhatTheyReceived_andTheRatingFollows() throws Exception {
        long productId = createProduct();
        when(orderClient.findOrder(501L)).thenReturn(Optional.of(order(501L, "DELIVERED", productId)));
        when(orderClient.findOrder(502L)).thenReturn(Optional.of(order(502L, "DELIVERED", productId)));
        when(orderClient.findOrder(503L)).thenReturn(Optional.of(order(503L, "OUT_FOR_DELIVERY", productId)));

        review(productId, 501L, 5).andExpect(status().isCreated()).andExpect(jsonPath("$.data.reviewerName").value("Priya S."));
        String second = review(productId, 502L, 2).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        review(productId, 501L, 4).andExpect(status().isConflict());              // once per order
        review(productId, 503L, 4).andExpect(status().isBadRequest());            // not delivered yet
        assertRating(productId, "3.5", 2);

        // Public list (no login), then an admin hides the 2-star review: it no longer counts
        mockMvc.perform(get("/api/v1/products/{id}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        Integer secondId = JsonPath.read(second, "$.data.id");
        mockMvc.perform(patch("/api/v1/admin/reviews/{id}/hide", secondId).header(HeaderNames.USER_ID, "2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"Not about the product\"}"))
                .andExpect(jsonPath("$.data.status").value("HIDDEN"));
        assertRating(productId, "5.0", 1);

        // The vendor sees the reviews of their own store only
        mockMvc.perform(get("/api/v1/vendor/reviews").header(HeaderNames.USER_ID, "30").header(HeaderNames.STORE_ID, String.valueOf(STORE)))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/vendor/reviews").header(HeaderNames.USER_ID, "31").header(HeaderNames.STORE_ID, "9"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    private ResultActions review(long productId, long orderId, int rating) throws Exception {
        return mockMvc.perform(post("/api/v1/products/{id}/reviews", productId).header(HeaderNames.USER_ID, String.valueOf(CUSTOMER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\": %d, \"rating\": %d, \"comment\": \"Fresh\"}".formatted(orderId, rating)));
    }

    private void assertRating(long productId, String average, int count) {
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getRatingAverage()).isEqualByComparingTo(average);
        assertThat(product.getRatingCount()).isEqualTo(count);
    }

    private OrderInfo order(long orderId, String status, long productId) {
        return new OrderInfo(orderId, CUSTOMER, STORE, status, List.of(new OrderInfo.Item(productId, "Sourdough")),
                new OrderInfo.Address("Priya Sharma"));
    }

    private long createProduct() throws Exception {
        String category = mockMvc.perform(post("/api/v1/admin/categories").header(HeaderNames.USER_ID, "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ \"name\": \"Breads\", \"displayOrder\": 9 }"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Integer categoryId = JsonPath.read(category, "$.data.id");
        String product = mockMvc.perform(post("/api/v1/vendor/products").header(HeaderNames.USER_ID, "30")
                        .header(HeaderNames.STORE_ID, String.valueOf(STORE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "categoryId": %d, "name": "Sourdough", "sku": "st8-sd-1", "unit": "G", "unitQuantity": 500,
                                  "price": 120.00, "discountPercentage": 0, "taxPercentage": 0 }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Integer) JsonPath.read(product, "$.data.id")).longValue();
    }
}
