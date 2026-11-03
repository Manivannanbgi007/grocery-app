package com.grocery.payment.service;

import com.grocery.payment.dto.common.PageResponse;
import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.dto.response.PaymentSummaryResponse;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

/**
 * Read-only payment queries for admins and other services.
 */
public interface PaymentQueryService {

    /** Every filter is optional. */
    PageResponse<PaymentResponse> searchPayments(PaymentStatus status, PaymentMethod method, Long storeId, Pageable pageable);

    PaymentResponse getPayment(Long paymentId);

    PaymentResponse getPaymentOfOrder(Long orderId);

    /** Money in and out for the payments created from "from" to "to" (both days included). */
    PaymentSummaryResponse getSummary(LocalDate from, LocalDate to);
}
