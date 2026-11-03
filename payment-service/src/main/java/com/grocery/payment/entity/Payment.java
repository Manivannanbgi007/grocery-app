package com.grocery.payment.entity;

import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The payment of one order (DDD aggregate root). One payment per order.
 * Every status change goes through a method that checks the current status (see {@link PaymentStatus}).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use Payment.forOrder(...)
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private Long orderId;

    @Column(name = "customer_user_id", nullable = false, updatable = false)
    private Long customerUserId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /** The gateway's order the customer pays against (ONLINE). */
    @Column(name = "gateway_order_id", length = 100)
    private String gatewayOrderId;

    /** The gateway's id of the successful payment (ONLINE). */
    @Column(name = "gateway_payment_id", length = 100)
    private String gatewayPaymentId;

    /** ONLINE: the customer must pay before this time, otherwise the payment fails (PaymentTimeoutJob). */
    @Column(name = "pay_before")
    private Instant payBefore;

    @Column(name = "refund_reference", length = 100)
    private String refundReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "refunded_at")
    private Instant refundedAt;

    public static Payment forOrder(Long orderId, Long customerUserId, Long storeId, BigDecimal amount, PaymentMethod method) {
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.customerUserId = customerUserId;
        payment.storeId = storeId;
        payment.amount = amount;
        payment.method = method;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    /** COD: the stock is reserved, so the order can go ahead; the cash is collected at the door. */
    public void acceptCashOnDelivery() {
        requireStatus(PaymentStatus.PENDING);
        requireMethod(PaymentMethod.COD);
        this.status = PaymentStatus.CASH_ON_DELIVERY;
    }

    /** ONLINE: the stock is reserved, the customer can pay now (until payBefore) against the gateway's order. */
    public void awaitOnlinePayment(String gatewayOrderId, Instant payBefore) {
        requireStatus(PaymentStatus.PENDING);
        requireMethod(PaymentMethod.ONLINE);
        this.status = PaymentStatus.AWAITING_PAYMENT;
        this.gatewayOrderId = gatewayOrderId;
        this.payBefore = payBefore;
    }

    /** Throws a message the customer understands when the payment cannot be paid (yet). */
    public void checkCanBePaidNow() {
        if (status == PaymentStatus.PENDING) {
            throw new BusinessException("We are still reserving your items. Please try again in a few seconds.");
        }
        if (status != PaymentStatus.AWAITING_PAYMENT) {
            throw new BusinessException("This payment cannot be paid (status " + status + ")");
        }
    }

    /** true when the customer did not pay before the deadline. */
    public boolean isOverdue(Instant now) {
        return status == PaymentStatus.AWAITING_PAYMENT && payBefore != null && now.isAfter(payBefore);
    }

    public void markPaid(String gatewayPaymentId, Instant paidAt) {
        requireStatus(PaymentStatus.AWAITING_PAYMENT);
        this.status = PaymentStatus.PAID;
        this.gatewayPaymentId = gatewayPaymentId;
        this.paidAt = paidAt;
        this.failureReason = null;   // an earlier failed attempt no longer matters
    }

    /**
     * Razorpay lets the customer try again after a failed attempt (card declined ...), so a failed
     * attempt only records why. The payment fails for good when the deadline passes.
     */
    public void recordFailedAttempt(String reason) {
        requireStatus(PaymentStatus.AWAITING_PAYMENT);
        this.failureReason = reason;
    }

    /**
     * The money arrived after the payment had already failed (deadline passed) or the order was cancelled.
     * The order cannot use it any more, so the caller refunds it right away (markRefunded).
     */
    public void acceptLatePayment(String gatewayPaymentId, Instant paidAt) {
        if (status != PaymentStatus.FAILED && status != PaymentStatus.CANCELLED) {
            throw new BusinessException("Payment of order %d is %s, a late payment is not expected".formatted(orderId, status));
        }
        this.status = PaymentStatus.PAID;
        this.gatewayPaymentId = gatewayPaymentId;
        this.paidAt = paidAt;
    }

    public void markFailed(String reason) {
        requireStatus(PaymentStatus.AWAITING_PAYMENT);
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    /** COD cash collected at the door. */
    public void markCollected(Instant collectedAt) {
        requireStatus(PaymentStatus.CASH_ON_DELIVERY);
        this.status = PaymentStatus.COLLECTED;
        this.paidAt = collectedAt;
    }

    /** true while nothing has been paid yet, so cancelling needs no refund. */
    public boolean canBeCancelledWithoutRefund() {
        return status == PaymentStatus.PENDING || status == PaymentStatus.AWAITING_PAYMENT || status == PaymentStatus.CASH_ON_DELIVERY;
    }

    public void cancel() {
        if (!canBeCancelledWithoutRefund()) {
            throw new BusinessException("A payment that is %s cannot simply be cancelled".formatted(status));
        }
        this.status = PaymentStatus.CANCELLED;
    }

    public boolean needsRefund() {
        return status == PaymentStatus.PAID;
    }

    public void markRefunded(String refundReference, Instant refundedAt) {
        requireStatus(PaymentStatus.PAID);
        this.status = PaymentStatus.REFUNDED;
        this.refundReference = refundReference;
        this.refundedAt = refundedAt;
    }

    private void requireStatus(PaymentStatus expected) {
        if (status != expected) {
            throw new BusinessException("Payment of order %d is %s, expected %s".formatted(orderId, status, expected));
        }
    }

    private void requireMethod(PaymentMethod expected) {
        if (method != expected) {
            throw new BusinessException("Payment of order %d is %s, not %s".formatted(orderId, method, expected));
        }
    }
}
