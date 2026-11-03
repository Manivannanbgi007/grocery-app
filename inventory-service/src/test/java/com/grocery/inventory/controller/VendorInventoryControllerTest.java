package com.grocery.inventory.controller;

import com.grocery.inventory.constant.HeaderNames;
import com.grocery.inventory.dto.response.StockResponse;
import com.grocery.inventory.exception.ExternalServiceException;
import com.grocery.inventory.service.VendorInventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VendorInventoryController.class)
class VendorInventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VendorInventoryService vendorInventoryService;

    @Test
    void setStock_usesTheStoreFromTheGatewayHeader() throws Exception {
        when(vendorInventoryService.setStock(eq(1L), eq(7L), any())).thenReturn(sampleStock());

        mockMvc.perform(put("/api/v1/vendor/inventory/7").header(HeaderNames.USER_ID, "3").header(HeaderNames.STORE_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantityOnHand": 40 }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.availableQuantity").value(40));
    }

    @Test
    void setStock_returns400WithFieldErrors_forInvalidNumbers() throws Exception {
        mockMvc.perform(put("/api/v1/vendor/inventory/7").header(HeaderNames.USER_ID, "3").header(HeaderNames.STORE_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantityOnHand": -5, "lowStockThreshold": -1 }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("quantityOnHand", "lowStockThreshold")));

        verifyNoInteractions(vendorInventoryService);
    }

    @Test
    void setStock_returns403_forAVendorWithoutAnApprovedStore() throws Exception {
        mockMvc.perform(put("/api/v1/vendor/inventory/7").header(HeaderNames.USER_ID, "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantityOnHand": 40 }
                                """))
                .andExpect(status().isForbidden());

        verifyNoInteractions(vendorInventoryService);
    }

    @Test
    void addStock_returns400_whenTheQuantityIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/inventory/7/additions").header(HeaderNames.USER_ID, "3").header(HeaderNames.STORE_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }

    @Test
    void setStock_returns503_whenTheProductCatalogIsDown() throws Exception {
        when(vendorInventoryService.setStock(eq(1L), eq(7L), any()))
                .thenThrow(new ExternalServiceException("The product catalog is not available right now. Please try again later.", null));

        mockMvc.perform(put("/api/v1/vendor/inventory/7").header(HeaderNames.USER_ID, "3").header(HeaderNames.STORE_ID, "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "quantityOnHand": 40 }
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false));
    }

    private StockResponse sampleStock() {
        return new StockResponse(7L, 1L, 40, 0, 40, 5, false, false, Instant.parse("2026-10-26T10:00:00Z"));
    }
}
