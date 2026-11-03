package com.grocery.order.entity;

import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An order (DDD aggregate root) and its STATE MACHINE.
 *
 * The status only changes through the methods below (confirm, accept, pack ... cancel). Each one checks
 * the allowed transitions in {@link OrderStatus} and adds a step to the tracking timeline (history).
 * Publishing the matching event is the service's job.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use Order.place(...)
@Entity
@Table(name = "orders")   // "order" is a reserved word in SQL
public class Order extends BaseEntity {

    @Column(name = "customer_user_id", nullable = false, updatable = false)
    private Long customerUserId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Embedded
    private StoreDetails store;

    @Embedded
    private DeliveryAddress deliveryAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 10)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private OrderPaymentStatus paymentStatus;

    /** The payment in payment-service that settled this order (from PaymentConfirmed); null for older orders. */
    @Column(name = "payment_id")
    private Long paymentId;

    @Embedded
    private OrderAmounts amounts;

    @Column(name = "customer_note", length = 250)
    private String customerNote;

    /** Why the order was cancelled or rejected. */
    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancelled_by", length = 20)
    private ChangedBy cancelledBy;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** Who delivers the order (from DeliveryAssigned), so the customer can call them. */
    @Column(name = "delivery_partner_name", length = 100)
    private String deliveryPartnerName;

    @Column(name = "delivery_partner_phone", length = 15)
    private String deliveryPartnerPhone;

    /** The Idempotency-Key of the request that placed the order (the chat sends one): a repeat finds this order. */
    @Column(name = "idempotency_key", length = 64, updatable = false)
    private String idempotencyKey;

    /** cart-service's fingerprint of the cart this order was made of: the same cart a moment later is a double order. */
    @Column(name = "checkout_fingerprint", length = 64, updatable = false)
    private String checkoutFingerprint;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id")
    private List<OrderStatusHistory> history = new ArrayList<>();

    /** A new order: PENDING until the stock is reserved and the payment is settled. */
    public static Order place(Long customerUserId, Long storeId, StoreDetails store, DeliveryAddress deliveryAddress,
                              PaymentMethod paymentMethod, OrderAmounts amounts, String customerNote, List<OrderItem> items) {
        Order order = new Order();
        order.customerUserId = customerUserId;
        order.storeId = storeId;
        order.store = store;
        order.deliveryAddress = deliveryAddress;
        order.paymentMethod = paymentMethod;
        order.paymentStatus = OrderPaymentStatus.PENDING;
        order.amounts = amounts;
        order.customerNote = customerNote;
        items.forEach(item -> {
            item.setOrder(order);
            order.items.add(item);
        });
        order.status = OrderStatus.PENDING;
        order.addHistory(OrderStatus.PENDING, "Order placed", ChangedBy.CUSTOMER);
        return order;
    }

    /**
     * How the new order was asked for, before it is saved.
     *
     * @param idempotencyKey      the request's Idempotency-Key; null without one (the website's checkout)
     * @param checkoutFingerprint the fingerprint of the cart it was made of
     */
    public void placedWith(String idempotencyKey, String checkoutFingerprint) {
        this.idempotencyKey = idempotencyKey;
        this.checkoutFingerprint = checkoutFingerprint;
    }

    // ----- saga steps (triggered by events) -----

    /** Stock reserved and payment settled. */
    public void confirm(OrderPaymentStatus settledPaymentStatus) {
        confirm(settledPaymentStatus, null);
    }

    /** Stock reserved and payment settled by the payment {@code paymentId} (it appears on the invoice). */
    public void confirm(OrderPaymentStatus settledPaymentStatus, Long paymentId) {
        moveTo(OrderStatus.CONFIRMED, paymentMethod == PaymentMethod.COD
                ? "Confirmed - pay cash on delivery" : "Payment received", ChangedBy.SYSTEM);
        this.paymentStatus = settledPaymentStatus;
        this.paymentId = paymentId;
    }

    public void markPaymentFailed() {
        this.paymentStatus = OrderPaymentStatus.FAILED;
    }

    public void markRefunded() {
        this.paymentStatus = OrderPaymentStatus.REFUNDED;
    }

    // ----- store steps -----

    public void accept() {
        moveTo(OrderStatus.ACCEPTED, "The store accepted your order", ChangedBy.VENDOR);
    }

    public void reject(String reason) {
        moveTo(OrderStatus.REJECTED, reason, ChangedBy.VENDOR);
        this.statusReason = reason;
        nothingToPayAnyMore();
    }

    public void pack() {
        moveTo(OrderStatus.PACKED, "Your items are packed", ChangedBy.VENDOR);
    }

    public void markReadyForPickup() {
        moveTo(OrderStatus.READY_FOR_PICKUP, "Waiting for a delivery partner", ChangedBy.VENDOR);
    }

    // ----- delivery steps (driven by delivery-service's events) -----

    /** A partner took the job (the status does not change: the order still waits at the store). */
    public void assignDeliveryPartner(String partnerName, String partnerPhone) {
        this.deliveryPartnerName = partnerName;
        this.deliveryPartnerPhone = partnerPhone;
    }

    public void markOutForDelivery(ChangedBy changedBy) {
        moveTo(OrderStatus.OUT_FOR_DELIVERY, "On the way to you" + (deliveryPartnerName == null ? "" : " with " + deliveryPartnerName), changedBy);
    }

    public void markDelivered(ChangedBy changedBy, Instant deliveredAt) {
        moveTo(OrderStatus.DELIVERED, "Delivered", changedBy);
        this.deliveredAt = deliveredAt;
        if (paymentMethod == PaymentMethod.COD) {
            this.paymentStatus = OrderPaymentStatus.PAID;   // the cash was collected at the door
        }
    }

    /** The partner could not deliver; the items go back to the store. */
    public void failDelivery(String reason) {
        moveTo(OrderStatus.DELIVERY_FAILED, reason, ChangedBy.DELIVERY_PARTNER);
        this.statusReason = reason;
        nothingToPayAnyMore();
    }

    // ----- cancelling -----

    /** Customer, admin or system. Only before the order leaves the store. */
    public void cancel(String reason, ChangedBy cancelledBy) {
        if (!status.isCancellable()) {
            throw new BusinessException("An order that is %s can no longer be cancelled".formatted(status));
        }
        moveTo(OrderStatus.CANCELLED, reason, cancelledBy);
        this.statusReason = reason;
        this.cancelledBy = cancelledBy;
        nothingToPayAnyMore();
    }

    /**
     * A cancelled or rejected order that was not paid yet owes nothing. A PAID order stays PAID
     * until payment-service reports the refund (RefundCompleted -> REFUNDED); FAILED stays FAILED.
     */
    private void nothingToPayAnyMore() {
        if (paymentStatus == OrderPaymentStatus.PENDING || paymentStatus == OrderPaymentStatus.CASH_ON_DELIVERY) {
            this.paymentStatus = OrderPaymentStatus.CANCELLED;
        }
    }

    public boolean isCancellable() {
        return status.isCancellable();
    }

    /** Paid online, or the cash was collected at delivery. Only then is the invoice final (and stored). */
    public boolean isPaid() {
        return paymentStatus == OrderPaymentStatus.PAID;
    }

    /**
     * An online order the customer still has to pay. Paying moves it to PAID, a failed payment to FAILED, and a
     * cancelled or rejected order to CANCELLED, so PENDING means "not paid yet, still possible".
     */
    public boolean awaitsOnlinePayment() {
        return paymentMethod == PaymentMethod.ONLINE && paymentStatus == OrderPaymentStatus.PENDING;
    }

    /** An invoice exists once the store accepted the order, unless the sale did not happen. */
    public boolean hasInvoice() {
        return status == OrderStatus.ACCEPTED || status == OrderStatus.PACKED || status == OrderStatus.READY_FOR_PICKUP
                || status == OrderStatus.OUT_FOR_DELIVERY || status == OrderStatus.DELIVERED;
    }

    /** INV-000123 for order 123. */
    public String getInvoiceNumber() {
        return "INV-%06d".formatted(getId());
    }

    public int itemCount() {
        return items.stream().mapToInt(OrderItem::getQuantity).sum();
    }

    private void moveTo(OrderStatus next, String note, ChangedBy changedBy) {
        if (!status.canMoveTo(next)) {
            throw new BusinessException("Order %d is %s, so it cannot become %s".formatted(getId(), status, next));
        }
        this.status = next;
        addHistory(next, note, changedBy);
    }

    private void addHistory(OrderStatus newStatus, String note, ChangedBy changedBy) {
        history.add(new OrderStatusHistory(this, newStatus, note, changedBy, Instant.now()));
    }
}
