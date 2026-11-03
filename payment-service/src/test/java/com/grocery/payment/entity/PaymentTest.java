package com.grocery.payment.entity;

import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The payment status rules (plain JUnit).
 */
class PaymentTest {

    private static final Instant PAY_BEFORE = Instant.parse("2026-10-26T10:15:00Z");

    @Test
    void onlinePayment_waitsForTheCustomer_thenIsPaid_andCanBeRefunded() {
        Payment payment = Payment.forOrder(10L, 4L, 1L, new BigDecimal("158.00"), PaymentMethod.ONLINE);

        payment.awaitOnlinePayment("mock_order_10", PAY_BEFORE);
        payment.markPaid("mock_pay_1", Instant.now());
        assertThat(payment.needsRefund()).isTrue();
        payment.markRefunded("mock_refund_1", Instant.now());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getGatewayOrderId()).isEqualTo("mock_order_10");
        assertThat(payment.getRefundReference()).isEqualTo("mock_refund_1");
    }

    @Test
    void cashOnDelivery_isCollectedWhenDelivered() {
        Payment payment = Payment.forOrder(11L, 4L, 1L, new BigDecimal("158.00"), PaymentMethod.COD);

        payment.acceptCashOnDelivery();
        payment.markCollected(Instant.now());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COLLECTED);
        assertThat(payment.getPaidAt()).isNotNull();
    }

    @Test
    void aPaymentCannotBePaidBeforeTheStockIsReserved() {
        Payment payment = Payment.forOrder(12L, 4L, 1L, new BigDecimal("158.00"), PaymentMethod.ONLINE);

        assertThatThrownBy(() -> payment.markPaid("mock_pay_2", Instant.now()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Payment of order 12 is PENDING, expected AWAITING_PAYMENT");
    }

    @Test
    void codCannotBeTreatedAsOnline() {
        Payment payment = Payment.forOrder(13L, 4L, 1L, new BigDecimal("158.00"), PaymentMethod.COD);

        assertThatThrownBy(() -> payment.awaitOnlinePayment("mock_order_13", PAY_BEFORE)).isInstanceOf(BusinessException.class);
    }

    @Test
    void cancellingWithoutRefund_onlyWhileNothingWasPaid() {
        Payment unpaid = Payment.forOrder(14L, 4L, 1L, BigDecimal.TEN, PaymentMethod.ONLINE);
        unpaid.awaitOnlinePayment("mock_order_14", PAY_BEFORE);
        unpaid.cancel();
        assertThat(unpaid.getStatus()).isEqualTo(PaymentStatus.CANCELLED);

        Payment paid = Payment.forOrder(15L, 4L, 1L, BigDecimal.TEN, PaymentMethod.ONLINE);
        paid.awaitOnlinePayment("mock_order_15", PAY_BEFORE);
        paid.markPaid("mock_pay_3", Instant.now());
        assertThat(paid.canBeCancelledWithoutRefund()).isFalse();
        assertThatThrownBy(paid::cancel).isInstanceOf(BusinessException.class);
    }

    @Test
    void anUnpaidOnlinePayment_isOverdueAfterItsDeadline() {
        Payment payment = Payment.forOrder(16L, 4L, 1L, BigDecimal.TEN, PaymentMethod.ONLINE);
        payment.awaitOnlinePayment("order_16", PAY_BEFORE);

        assertThat(payment.isOverdue(PAY_BEFORE.minusSeconds(1))).isFalse();
        assertThat(payment.isOverdue(PAY_BEFORE.plusSeconds(1))).isTrue();

        payment.markPaid("pay_16", PAY_BEFORE.minusSeconds(5));
        assertThat(payment.isOverdue(PAY_BEFORE.plusSeconds(1))).isFalse();   // paid: no deadline any more
    }

    @Test
    void aFailedAttempt_keepsThePaymentOpen_untilTheCustomerPays() {
        Payment payment = Payment.forOrder(17L, 4L, 1L, BigDecimal.TEN, PaymentMethod.ONLINE);
        payment.awaitOnlinePayment("order_17", PAY_BEFORE);

        payment.recordFailedAttempt("Card declined by the bank");
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        assertThat(payment.getFailureReason()).isEqualTo("Card declined by the bank");

        payment.markPaid("pay_17", Instant.now());
        assertThat(payment.getFailureReason()).isNull();
    }

    @Test
    void moneyArrivingAfterTheDeadline_isAcceptedOnlyToBeRefunded() {
        Payment payment = Payment.forOrder(18L, 4L, 1L, BigDecimal.TEN, PaymentMethod.ONLINE);
        payment.awaitOnlinePayment("order_18", PAY_BEFORE);
        payment.markFailed("Not paid in time");

        payment.acceptLatePayment("pay_18", Instant.now());
        assertThat(payment.needsRefund()).isTrue();
        payment.markRefunded("rfnd_18", Instant.now());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void checkCanBePaidNow_explainsWhyNot() {
        Payment payment = Payment.forOrder(19L, 4L, 1L, BigDecimal.TEN, PaymentMethod.ONLINE);
        assertThatThrownBy(payment::checkCanBePaidNow).hasMessageContaining("still reserving your items");

        payment.awaitOnlinePayment("order_19", PAY_BEFORE);
        payment.checkCanBePaidNow();   // no exception

        payment.markFailed("Not paid in time");
        assertThatThrownBy(payment::checkCanBePaidNow).hasMessage("This payment cannot be paid (status FAILED)");
    }
}
