package com.grocery.payment.service.impl;

import com.grocery.payment.config.PaymentProperties;
import com.grocery.payment.dto.common.PageResponse;
import com.grocery.payment.dto.response.PaymentResponse;
import com.grocery.payment.dto.response.PaymentSummaryResponse;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.exception.BusinessException;
import com.grocery.payment.exception.ResourceNotFoundException;
import com.grocery.payment.mapper.PaymentMapper;
import com.grocery.payment.repository.PaymentRepository;
import com.grocery.payment.repository.PaymentTotals;
import com.grocery.payment.service.PaymentQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentQueryServiceImpl implements PaymentQueryService {

    private static final int MAX_SUMMARY_DAYS = 366;

    private final PaymentRepository paymentRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentProperties paymentProperties;

    @Override
    public PageResponse<PaymentResponse> searchPayments(PaymentStatus status, PaymentMethod method, Long storeId, Pageable pageable) {
        return PageResponse.from(paymentRepository.search(status, method, storeId, pageable).map(paymentMapper::toResponse));
    }

    @Override
    public PaymentResponse getPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }

    @Override
    public PaymentResponse getPaymentOfOrder(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(paymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No payment found for order " + orderId));
    }

    @Override
    public PaymentSummaryResponse getSummary(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_SUMMARY_DAYS) {
            throw new BusinessException("The summary covers at most " + MAX_SUMMARY_DAYS + " days");
        }
        ZoneId zone = paymentProperties.timeZone();
        List<PaymentTotals> totals = paymentRepository.totalsCreatedBetween(
                from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());

        BigDecimal paidOnline = sum(totals, PaymentMethod.ONLINE, PaymentStatus.PAID);
        BigDecimal collectedCash = sum(totals, PaymentMethod.COD, PaymentStatus.COLLECTED);
        BigDecimal refunded = sum(totals, PaymentMethod.ONLINE, PaymentStatus.REFUNDED);
        return new PaymentSummaryResponse(from, to, paidOnline, collectedCash, refunded, paidOnline.add(collectedCash), totals);
    }

    private BigDecimal sum(List<PaymentTotals> totals, PaymentMethod method, PaymentStatus status) {
        return totals.stream()
                .filter(row -> row.method() == method && row.status() == status)
                .map(PaymentTotals::amount)
                .findFirst()
                .orElse(BigDecimal.ZERO.setScale(2));
    }
}
