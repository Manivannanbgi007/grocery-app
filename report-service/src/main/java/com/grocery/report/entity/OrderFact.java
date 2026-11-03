package com.grocery.report.entity;

import com.grocery.report.enums.ReportOrderStatus;
import com.grocery.report.enums.ReportPaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What reports need to know about one order. The id is order-service's order id (no id of our own).
 *
 * "sale" and "cancelled" are kept as columns, so report queries stay simple:
 *   sale      = confirmed (or delivered) and not ended without delivery -> counts for sales and top lists
 *   cancelled = cancelled, rejected or delivery failed
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use OrderFact.placed(...)
@Entity
@Table(name = "order_facts")
public class OrderFact {

    @Id
    @Column(name = "order_id")
    private Long orderId;

    /** Order events and payment events are handled by two threads; @Version stops one from overwriting the other. */
    @Version
    private Long version;

    @Column(name = "customer_user_id", nullable = false)
    private Long customerUserId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportOrderStatus status;

    @Column(nullable = false)
    private boolean sale;

    @Column(nullable = false)
    private boolean cancelled;

    @Column(name = "payment_method", nullable = false, length = 10)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private ReportPaymentStatus paymentStatus;

    @Column(name = "items_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal itemsTotal;

    @Column(name = "coupon_discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal couponDiscount;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "commission_percentage", precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    @Column(name = "placed_at", nullable = false)
    private Instant placedAt;

    @Column(name = "placed_date", nullable = false)
    private LocalDate placedDate;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "delivered_date")
    private LocalDate deliveredDate;

    @Column(name = "ended_at")
    private Instant endedAt;

    public static OrderFact placed(Long orderId, Long customerUserId, Long storeId, String paymentMethod, BigDecimal itemsTotal,
                                   BigDecimal couponDiscount, BigDecimal deliveryFee, BigDecimal totalAmount,
                                   BigDecimal commissionPercentage, Instant placedAt, LocalDate placedDate) {
        OrderFact fact = new OrderFact();
        fact.orderId = orderId;
        fact.customerUserId = customerUserId;
        fact.storeId = storeId;
        fact.status = ReportOrderStatus.PENDING;
        fact.paymentMethod = paymentMethod;
        fact.paymentStatus = ReportPaymentStatus.PENDING;
        fact.itemsTotal = itemsTotal;
        fact.couponDiscount = couponDiscount;
        fact.deliveryFee = deliveryFee;
        fact.totalAmount = totalAmount;
        fact.commissionPercentage = commissionPercentage;
        fact.placedAt = placedAt;
        fact.placedDate = placedDate;
        return fact;
    }

    /** Stock reserved and payment settled: from now on it is a sale. */
    public void confirm() {
        if (status == ReportOrderStatus.PENDING) {
            this.status = ReportOrderStatus.CONFIRMED;
            this.sale = true;
        }
    }

    public void deliver(Instant deliveredAt, LocalDate deliveredDate) {
        this.status = ReportOrderStatus.DELIVERED;
        this.sale = true;
        this.deliveredAt = deliveredAt;
        this.deliveredDate = deliveredDate;
        if (paymentStatus == ReportPaymentStatus.CASH_ON_DELIVERY) {
            this.paymentStatus = ReportPaymentStatus.PAID;   // cash collected at the door
        }
    }

    /** Cancelled, rejected or not delivered: no sale. */
    public void end(ReportOrderStatus endStatus, Instant endedAt) {
        this.status = endStatus;
        this.sale = false;
        this.cancelled = true;
        this.endedAt = endedAt;
    }

    public void paymentReceived(String method) {
        this.paymentStatus = "COD".equals(method) ? ReportPaymentStatus.CASH_ON_DELIVERY : ReportPaymentStatus.PAID;
    }

    public void refunded() {
        this.paymentStatus = ReportPaymentStatus.REFUNDED;
    }
}
