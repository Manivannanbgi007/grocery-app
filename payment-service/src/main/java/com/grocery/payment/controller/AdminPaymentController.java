package com.grocery.payment.controller;

import com.grocery.payment.dto.common.ApiResponse;
import com.grocery.payment.dto.common.PageResponse;
import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.dto.response.PaymentSummaryResponse;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.service.PaymentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Admin panel: all payments and refunds.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Payments", description = "All payments and refunds (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/payments")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentQueryService paymentQueryService;

    @Operation(summary = "Search payments, e.g. status=REFUNDED or method=COD")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PaymentResponse>>> searchPayments(
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) Long storeId,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Payments fetched successfully",
                paymentQueryService.searchPayments(status, method, storeId, pageable)));
    }

    @Operation(summary = "Money in and out: online paid, cash collected, refunded (payments created in the period, India time)")
    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<PaymentSummaryResponse>> getSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Payment summary calculated", paymentQueryService.getSummary(from, to)));
    }

    @Operation(summary = "One payment")
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPayment(@PathVariable Long paymentId) {
        return ResponseEntity.ok(ApiResponse.success("Payment fetched successfully", paymentQueryService.getPayment(paymentId)));
    }
}
