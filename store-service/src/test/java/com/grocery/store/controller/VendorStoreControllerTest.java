package com.grocery.store.controller;

import com.grocery.store.constant.HeaderNames;
import com.grocery.store.dto.response.StoreResponse;
import com.grocery.store.enums.StoreStatus;
import com.grocery.store.service.VendorStoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VendorStoreController.class)
class VendorStoreControllerTest {

    private static final String VALID_STORE = """
            { "name": "Daily Needs Supermarket", "phone": "9845012345", "addressLine1": "12, 5th Cross",
              "city": "Bengaluru", "state": "Karnataka", "pincode": "560038" }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VendorStoreService vendorStoreService;

    @Test
    void registerStore_returns201_forTheVendorInTheGatewayHeader() throws Exception {
        when(vendorStoreService.registerStore(eq(10L), any())).thenReturn(sampleStore());

        mockMvc.perform(post("/api/v1/vendor/store").header(HeaderNames.USER_ID, "10")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_STORE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    @Test
    void registerStore_returns400WithFieldErrors_whenDetailsAreInvalid() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/store").header(HeaderNames.USER_ID, "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "name": "", "phone": "123", "addressLine1": "x", "city": "Bengaluru",
                                  "state": "Karnataka", "pincode": "0123" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("name", "phone", "pincode")));

        verifyNoInteractions(vendorStoreService);
    }

    @Test
    void registerStore_returns401_withoutTheGatewayUserHeader() throws Exception {
        mockMvc.perform(post("/api/v1/vendor/store").contentType(MediaType.APPLICATION_JSON).content(VALID_STORE))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateOpeningHours_acceptsDaysWithoutTheOptionalClosedFlag() throws Exception {
        when(vendorStoreService.updateOpeningHours(eq(10L), any())).thenReturn(sampleStore());

        // "closed" is left out: it is optional (Jackson 3 would reject a missing primitive boolean)
        mockMvc.perform(put("/api/v1/vendor/store/opening-hours").header(HeaderNames.USER_ID, "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "days": [ { "dayOfWeek": "MONDAY", "opensAt": "09:00", "closesAt": "18:00" } ] }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void updateDeliveryAreas_rejectsInvalidPincodes() throws Exception {
        mockMvc.perform(put("/api/v1/vendor/store/delivery-areas").header(HeaderNames.USER_ID, "10")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "pincodes": ["560038", "ABCDEF"] }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(vendorStoreService);
    }

    private StoreResponse sampleStore() {
        return new StoreResponse(3L, 10L, "Daily Needs Supermarket", null, "9845012345", null, "12, 5th Cross", null,
                "Bengaluru", "Karnataka", "560038", null, null, StoreStatus.PENDING_APPROVAL, null,
                new BigDecimal("10.00"), true, false, List.of(), List.of("560038"), null, null);
    }
}
