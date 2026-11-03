package com.grocery.delivery.tracking;

import com.grocery.delivery.entity.Delivery;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Notices EVERY status change of a delivery, wherever it happens: the partner accepts or delivers, an admin
 * assigns, the automatic assignment, a cancelled order ... - without a line of tracking code in all those places.
 *
 * JPA calls this after a delivery was inserted or updated (Delivery has @EntityListeners(DeliveryStatusListener.class)).
 * Spring creates the listener, so it gets the ApplicationEventPublisher. It only hands a DeliveryStatusChanged event to
 * Spring; TrackingStatusPublisher sends it out AFTER the transaction committed, so a change that is rolled back never
 * reaches a customer.
 */
public class DeliveryStatusListener {

    private final ApplicationEventPublisher eventPublisher;

    public DeliveryStatusListener(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostPersist
    @PostUpdate
    void afterSave(Delivery delivery) {
        if (delivery.takeStatusChange()) {
            eventPublisher.publishEvent(new DeliveryStatusChanged(delivery.getId(), delivery.getOrderId(), delivery.getStatus()));
        }
    }
}
