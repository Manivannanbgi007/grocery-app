package com.grocery.payment.controller;

import com.grocery.payment.dto.common.ApiResponse;
import com.grocery.payment.service.RazorpayPaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Razorpay calls this URL when something happens to a payment (set it up in the Razorpay dashboard:
 * Settings -> Webhooks, events payment.captured and payment.failed).
 *
 * Nobody logs in here: the gateway lets it through without a token, and the signature header proves
 * the call comes from Razorpay. The body is read as a String because the signature is calculated over
 * the exact bytes Razorpay sent.
 *
 * Razorpay sends a webhook again until it gets a 2xx answer, so only a wrong signature returns an error.
 */
@Tag(name = "Razorpay webhook", description = "Called by Razorpay, not by users")
@RestController
@RequestMapping("/api/v1/payments/webhooks")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.payment.gateway", havingValue = "razorpay")
public class RazorpayWebhookController {

    private final RazorpayPaymentService razorpayPaymentService;

    @Operation(summary = "Payment events from Razorpay (payment.captured, payment.failed)")
    @PostMapping("/razorpay")
    public ResponseEntity<ApiResponse<Void>> receive(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
            @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId) {
        String result = razorpayPaymentService.handleWebhook(rawBody, signature, eventId);
        return ResponseEntity.ok(ApiResponse.success(result, null));
    }
}
