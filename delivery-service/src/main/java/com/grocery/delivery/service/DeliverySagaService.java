package com.grocery.delivery.service;

import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderReadyForPickup;
import com.grocery.events.user.UserRegistered;

/**
 * How delivery-service reacts to other services' events. Every method is idempotent.
 */
public interface DeliverySagaService {

    /** A new DELIVERY_PARTNER user gets a partner profile (KYC NOT_SUBMITTED). */
    void handleUserRegistered(UserRegistered event);

    /** A packed order waits at the store: create the delivery and look for a partner. */
    void handleOrderReadyForPickup(OrderReadyForPickup event);

    /** The order was cancelled: cancel its delivery (or send the items back if they were already picked up). */
    void handleOrderCancelled(OrderCancelled event);
}
