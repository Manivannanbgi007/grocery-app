package com.grocery.order.controller;

import com.grocery.order.constant.HeaderNames;
import com.grocery.order.dto.request.PlaceOrderRequest;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.exception.CheckoutChangedException;
import com.grocery.order.exception.CheckoutNotReadyException;
import com.grocery.order.exception.ExternalServiceException;
import com.grocery.order.exception.InvoiceUnavailableException;
import com.grocery.order.service.CustomerOrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerOrderController.class)
class CustomerOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CustomerOrderService customerOrderService;

    @Test
    void placeOrder_returns400_forAMissingAddressOrAnUnknownPaymentMethod() throws Exception {
        mockMvc.perform(post("/api/v1/orders").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "paymentMethod": "COD" }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field", hasItems("addressId")));

        mockMvc.perform(post("/api/v1/orders").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "addressId": 1, "paymentMethod": "BITCOIN" }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(customerOrderService);
    }

    @Test
    void placeOrder_cartProblems_return409_withEveryProblem() throws Exception {
        when(customerOrderService.placeOrder(eq(4L), any(), any()))
                .thenThrow(new CheckoutNotReadyException(List.of("Tomato is out of stock", "Coupon SAVE10 has expired")));

        mockMvc.perform(post("/api/v1/orders").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "addressId": 1, "paymentMethod": "COD" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[*].message", hasItems("Tomato is out of stock", "Coupon SAVE10 has expired")));
    }

    @Test
    void placeOrder_whenTheCartServiceIsDown_returns503() throws Exception {
        when(customerOrderService.placeOrder(eq(4L), any(), any()))
                .thenThrow(new ExternalServiceException("Your cart is not available right now. Please try again later.", null));

        mockMvc.perform(post("/api/v1/orders").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "addressId": 1, "paymentMethod": "ONLINE" }
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void placeOrder_passesTheIdempotencyKeyOn_andRefusesAMalformedOne() throws Exception {
        mockMvc.perform(post("/api/v1/orders").header(HeaderNames.USER_ID, "4")
                        .header(HeaderNames.IDEMPOTENCY_KEY, "chat-card-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "addressId": 1, "paymentMethod": "COD", "expectedCheckoutFingerprint": "fp-ready-cart-001" }
                                """))
                .andExpect(status().isCreated());
        verify(customerOrderService).placeOrder(eq(4L),
                eq(new PlaceOrderRequest(1L, PaymentMethod.COD, null, "fp-ready-cart-001")), eq("chat-card-0001"));

        mockMvc.perform(post("/api/v1/orders").header(HeaderNames.USER_ID, "4")
                        .header(HeaderNames.IDEMPOTENCY_KEY, "a b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "addressId": 1, "paymentMethod": "COD" }
                                """))
                .andExpect(status().isBadRequest());
        verifyNoMoreInteractions(customerOrderService);
    }

    @Test
    void placeOrder_fromASummaryOfAChangedCart_returns409() throws Exception {
        when(customerOrderService.placeOrder(eq(4L), any(), any())).thenThrow(new CheckoutChangedException());

        mockMvc.perform(post("/api/v1/orders").header(HeaderNames.USER_ID, "4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "addressId": 1, "paymentMethod": "COD", "expectedCheckoutFingerprint": "fp-old" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Your cart or its prices changed since the summary. Please check the new total."));
    }

    @Test
    void invoice_whenThePdfCannotBeGenerated_returns503_askingToTryAgain() throws Exception {
        when(customerOrderService.getMyInvoice(4L, 10L))
                .thenThrow(new InvoiceUnavailableException(new IllegalStateException("PDF library failed")));

        mockMvc.perform(get("/api/v1/orders/10/invoice").header(HeaderNames.USER_ID, "4"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The invoice could not be created right now. Please try again in a moment."));
    }

    @Test
    void cancel_worksWithoutABody() throws Exception {
        mockMvc.perform(post("/api/v1/orders/10/cancel").header(HeaderNames.USER_ID, "4"))
                .andExpect(status().isOk());
    }
}
