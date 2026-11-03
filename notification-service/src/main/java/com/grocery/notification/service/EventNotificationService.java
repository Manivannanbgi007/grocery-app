package com.grocery.notification.service;

import com.grocery.events.delivery.DeliveryAssigned;
import com.grocery.events.delivery.PartnerKycReviewed;
import com.grocery.events.inventory.LowStockDetected;
import com.grocery.events.order.OrderAccepted;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderOutForDelivery;
import com.grocery.events.order.OrderPacked;
import com.grocery.events.order.OrderRejected;
import com.grocery.events.payment.PaymentFailed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.events.store.StoreApproved;
import com.grocery.events.store.StoreRegistered;
import com.grocery.events.store.StoreStatusChanged;
import com.grocery.events.user.PasswordResetRequested;
import com.grocery.events.user.SocialLoginLinked;
import com.grocery.events.user.UserRegistered;

/**
 * Turns the platform's events into notifications. Every method is idempotent (processed_event table):
 * a duplicate event never sends a second email.
 */
public interface EventNotificationService {

    // users and stores (also keep the contact book up to date)
    void handleUserRegistered(UserRegistered event);

    void handlePasswordResetRequested(PasswordResetRequested event);

    void handleSocialLoginLinked(SocialLoginLinked event);

    void handleStoreRegistered(StoreRegistered event);

    void handleStoreApproved(StoreApproved event);

    void handleStoreStatusChanged(StoreStatusChanged event);

    // orders
    void handleOrderConfirmed(OrderConfirmed event);

    void handleOrderAccepted(OrderAccepted event);

    void handleOrderRejected(OrderRejected event);

    void handleOrderPacked(OrderPacked event);

    void handleOrderOutForDelivery(OrderOutForDelivery event);

    void handleOrderDelivered(OrderDelivered event);

    void handleOrderCancelled(OrderCancelled event);

    void handleOrderDeliveryFailed(OrderDeliveryFailed event);

    // delivery
    void handleDeliveryAssigned(DeliveryAssigned event);

    void handlePartnerKycReviewed(PartnerKycReviewed event);

    // payments and stock
    void handlePaymentFailed(PaymentFailed event);

    void handleRefundCompleted(RefundCompleted event);

    void handleLowStockDetected(LowStockDetected event);
}
