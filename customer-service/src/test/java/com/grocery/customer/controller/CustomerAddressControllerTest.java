package com.grocery.customer.controller;

import com.grocery.customer.constant.HeaderNames;
import com.grocery.customer.dto.response.AddressResponse;
import com.grocery.customer.enums.AddressLabel;
import com.grocery.customer.exception.ResourceNotFoundException;
import com.grocery.customer.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerAddressController.class)
class CustomerAddressControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerService customerService;

    @Test
    void addAddress_returns201() throws Exception {
        when(customerService.addAddress(eq(4L), any())).thenReturn(new AddressResponse(5L, AddressLabel.HOME, "Priya Sharma",
                "9000000004", "Flat 302", null, null, "Bengaluru", "Karnataka", "560038", null, null, true));

        mockMvc.perform(post("/api/v1/customers/me/addresses").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "label": "HOME", "contactName": "Priya Sharma", "contactPhone": "9000000004",
                                  "addressLine1": "Flat 302", "city": "Bengaluru", "state": "Karnataka", "pincode": "560038" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.data.defaultAddress").value(true));
    }

    @Test
    void addAddress_returns400WithFieldErrors_forInvalidData() throws Exception {
        mockMvc.perform(post("/api/v1/customers/me/addresses").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "label": "HOME", "contactName": "", "contactPhone": "12345",
                                  "addressLine1": "Flat 302", "city": "Bengaluru", "state": "Karnataka", "pincode": "56003" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("contactName", "contactPhone", "pincode")));

        verifyNoInteractions(customerService);
    }

    @Test
    void deleteAddress_returns404_whenItIsNotMine() throws Exception {
        doThrow(new ResourceNotFoundException("Address", 99L)).when(customerService).deleteAddress(4L, 99L);

        mockMvc.perform(delete("/api/v1/customers/me/addresses/99").header(HeaderNames.USER_ID, "4"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Address not found with id: 99"));
    }
}
