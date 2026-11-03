package com.grocery.product.controller;

import com.grocery.product.constant.HeaderNames;
import com.grocery.product.service.VendorProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VendorProductController.class)
@ActiveProfiles("test")
class VendorProductControllerTest {

    private static final String VALID_PRODUCT = """
            { "categoryId": 1, "name": "Cherry Tomato", "sku": "FM-VEG-020", "unit": "G",
              "unitQuantity": 250, "price": 45.00, "taxPercentage": 0 }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VendorProductService vendorProductService;

    @Test
    void createProduct_returns403_whenTheVendorHasNoApprovedStore() throws Exception {
        // A vendor without an approved store has no X-Store-Id header
        mockMvc.perform(post("/api/v1/vendor/products").header(HeaderNames.USER_ID, "10")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_PRODUCT))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "No store found for this vendor. Register a store and wait for approval, then log in again."));
        verifyNoInteractions(vendorProductService);
    }

    @Test
    void createProduct_returns400WithFieldErrors_forInvalidData() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/products").header(HeaderNames.USER_ID, "3").header(HeaderNames.STORE_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "categoryId": 1, "name": "", "sku": "bad sku!", "unit": "G",
                                  "unitQuantity": 0, "price": 0, "discountPercentage": 95, "taxPercentage": 0 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("name", "sku", "unitQuantity", "price", "discountPercentage")));
        verifyNoInteractions(vendorProductService);
    }

    @Test
    void addImage_returns400_whenNoFileIsSent() throws Exception {
        mockMvc.perform(multipart("/api/v1/vendor/products/1/images")
                        .header(HeaderNames.USER_ID, "3").header(HeaderNames.STORE_ID, "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Required file 'image' is missing"));
    }
}
