package com.grocery.report.service;

import com.grocery.events.delivery.PartnerKycReviewed;
import com.grocery.events.inventory.StockChanged;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.events.store.StoreApproved;
import com.grocery.events.store.StoreRegistered;
import com.grocery.events.store.StoreStatusChanged;
import com.grocery.events.user.UserRegistered;
import com.grocery.report.enums.ReportOrderStatus;

import java.time.Instant;

/**
 * Keeps the read model up to date (the "projection" in CQRS terms). One method per event; every method
 * is idempotent: a duplicate event is skipped.
 */
public interface ReportProjectionService {

    void onUserRegistered(UserRegistered event);

    void onStoreRegistered(StoreRegistered event);

    void onStoreApproved(StoreApproved event);

    void onStoreStatusChanged(StoreStatusChanged event);

    void onOrderCreated(OrderCreated event);

    void onOrderConfirmed(OrderConfirmed event);

    void onOrderDelivered(OrderDelivered event);

    /** OrderCancelled, OrderRejected, OrderDeliveryFailed. */
    void onOrderEnded(String eventId, String eventType, Long orderId, ReportOrderStatus endStatus, Instant endedAt);

    /** Comes on another topic than OrderCreated: if the order is not known yet, it fails and Kafka retries. */
    void onPaymentConfirmed(PaymentConfirmed event);

    void onRefundCompleted(RefundCompleted event);

    void onStockChanged(StockChanged event);

    void onPartnerKycReviewed(PartnerKycReviewed event);
}
