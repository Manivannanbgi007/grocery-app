package com.grocery.payment.controller;

import com.grocery.payment.exception.BusinessException;
import com.grocery.payment.service.RazorpayPaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The webhook needs no login and must pass the body on byte for byte (the signature covers it).
 */
@WebMvcTest(controllers = RazorpayWebhookController.class, properties = "app.payment.gateway=razorpay")
class RazorpayWebhookControllerTest {

    private static final String BODY = "{\"event\":\"payment.captured\",  \"payload\":{}}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RazorpayPaymentService razorpayPaymentService;

    @Test
    void theRawBodyAndHeadersReachTheService_withoutAnyUserHeaders() throws Exception {
        when(razorpayPaymentService.handleWebhook(BODY, "sig", "evt_1")).thenReturn("Processed: payment.captured");

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .header("X-Razorpay-Signature", "sig")
                        .header("X-Razorpay-Event-Id", "evt_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Processed: payment.captured"));

        verify(razorpayPaymentService).handleWebhook(BODY, "sig", "evt_1");
    }

    @Test
    void aWrongSignature_is400() throws Exception {
        when(razorpayPaymentService.handleWebhook(BODY, "forged", null)).thenThrow(new BusinessException("Invalid webhook signature"));

        mockMvc.perform(post("/api/v1/payments/webhooks/razorpay")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY)
                        .header("X-Razorpay-Signature", "forged"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid webhook signature"));
    }
}
