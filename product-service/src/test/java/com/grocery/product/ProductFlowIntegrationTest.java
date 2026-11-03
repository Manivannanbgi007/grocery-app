package com.grocery.product;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end on a real MySQL (Testcontainers): admin creates a category, a vendor manages a product
 * (price, image, status), another store cannot touch it, and an admin block wins over the vendor.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ProductFlowIntegrationTest {

    private static final byte[] FAKE_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01, 0x02};

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ProductRepository productRepository;

    @Test
    void vendorManagesAProduct_withinTheRulesOfStoreAndAdmin() throws Exception {
        // 1. Admin creates a category
        String category = mockMvc.perform(post("/api/v1/admin/categories").header(HeaderNames.USER_ID, "2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "Bakery & Bread", "displayOrder": 6 }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("bakery-bread"))
                .andReturn().getResponse().getContentAsString();
        Integer categoryId = JsonPath.read(category, "$.data.id");

        // 2. Vendor of store 7 adds a product
        String created = mockMvc.perform(asStore(post("/api/v1/vendor/products"), 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "categoryId": %d, "name": "Whole Wheat Bread", "brand": "Modern", "sku": "st7-brd-001",
                                  "unit": "G", "unitQuantity": 400, "price": 50.00, "discountPercentage": 10, "taxPercentage": 0 }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sellingPrice").value(45.00))
                .andExpect(jsonPath("$.data.sku").value("ST7-BRD-001"))
                .andReturn().getResponse().getContentAsString();
        Integer productId = JsonPath.read(created, "$.data.id");

        // 3. Change the price -> the selling price follows
        mockMvc.perform(asStore(put("/api/v1/vendor/products/{id}", productId), 7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "categoryId": %d, "name": "Whole Wheat Bread", "brand": "Modern", "sku": "ST7-BRD-001",
                                  "unit": "G", "unitQuantity": 400, "price": 60.00, "discountPercentage": 25, "taxPercentage": 0 }
                                """.formatted(categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sellingPrice").value(45.00));

        // 4. Upload an image -> stored in the JSON column and served at /media/**
        MockMultipartFile image = new MockMultipartFile("image", "bread.jpg", "image/jpeg", FAKE_JPEG);
        String withImage = mockMvc.perform(asStore(multipart("/api/v1/vendor/products/{id}/images", productId).file(image), 7))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String imageUrl = JsonPath.read(withImage, "$.data.imageUrls[0]");
        assertThat(imageUrl).startsWith("http://localhost:8080/media/products/" + productId + "/");

        Product saved = productRepository.findById(productId.longValue()).orElseThrow();
        assertThat(saved.getImageUrls()).containsExactly(imageUrl);
        mockMvc.perform(get(imageUrl.replace("http://localhost:8080", "")))
                .andExpect(status().isOk())
                .andExpect(content().bytes(FAKE_JPEG));

        // 5. Another store cannot change this product
        mockMvc.perform(asStore(patch("/api/v1/vendor/products/{id}/status", productId), 8)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "status": "INACTIVE" }
                                """))
                .andExpect(status().isNotFound());

        // 6. An admin blocks it -> the vendor cannot activate it again
        mockMvc.perform(post("/api/v1/admin/products/{id}/block", productId).header(HeaderNames.USER_ID, "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"));
        mockMvc.perform(asStore(patch("/api/v1/vendor/products/{id}/status", productId), 7)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "status": "ACTIVE" }
                                """))
                .andExpect(status().isBadRequest());

        // 7. The vendor deletes it -> soft delete, then it is gone for the vendor
        mockMvc.perform(asStore(delete("/api/v1/vendor/products/{id}", productId), 7)).andExpect(status().isOk());
        mockMvc.perform(asStore(delete("/api/v1/vendor/products/{id}", productId), 7)).andExpect(status().isNotFound());
        assertThat(productRepository.findById(productId.longValue())).get()
                .extracting(product -> product.getStatus().name()).isEqualTo("DELETED");
    }

    @Test
    void sameSkuInTwoStores_isAllowed_butNotTwiceInOneStore() throws Exception {
        String category = mockMvc.perform(post("/api/v1/admin/categories").header(HeaderNames.USER_ID, "2")
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                { "name": "Pet Care" }
                                """))
                .andReturn().getResponse().getContentAsString();
        Integer categoryId = JsonPath.read(category, "$.data.id");
        String product = """
                { "categoryId": %d, "name": "Dog Food", "sku": "PET-001", "unit": "KG",
                  "unitQuantity": 1, "price": 400, "taxPercentage": 18 }
                """.formatted(categoryId);

        mockMvc.perform(asStore(post("/api/v1/vendor/products"), 21).contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated());
        mockMvc.perform(asStore(post("/api/v1/vendor/products"), 22).contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated());
        mockMvc.perform(asStore(post("/api/v1/vendor/products"), 21).contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isConflict());
    }

    /** The headers the API gateway adds for a vendor of the given store (works for normal and multipart requests). */
    private <B extends AbstractMockHttpServletRequestBuilder<B>> B asStore(B request, long storeId) {
        return request.header(HeaderNames.USER_ID, "500" + storeId)
                .header(HeaderNames.USER_ROLE, "VENDOR")
                .header(HeaderNames.STORE_ID, String.valueOf(storeId));
    }
}
