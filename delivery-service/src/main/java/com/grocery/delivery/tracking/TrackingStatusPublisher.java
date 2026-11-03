package com.grocery.delivery.tracking;

import com.grocery.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * After a status change was committed: tell the customer's live tracking, and forget the partner's position as soon
 * as the delivery is no longer tracked (delivered, cancelled ...) - location data is kept only while it is needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingStatusPublisher {

    private final DeliveryRepository deliveryRepository;
    private final LiveLocationStore liveLocationStore;
    private final TrackingSnapshotFactory snapshotFactory;
    private final TrackingBroadcaster broadcaster;

    /** Runs after the commit (fallbackExecution: also when a change was saved without a transaction). */
    @TransactionalEventListener(fallbackExecution = true)
    public void onStatusChanged(DeliveryStatusChanged event) {
        try {
            if (!event.status().isTracked()) {
                liveLocationStore.delete(event.deliveryId());
            }
            deliveryRepository.findById(event.deliveryId())
                    .ifPresent(delivery -> broadcaster.publish(snapshotFactory.build(delivery)));
        } catch (RuntimeException ex) {
            // the status change itself is saved; only the live update failed (e.g. Redis down): the page reloads it
            log.warn("Could not publish the tracking update of order id={}: {}", event.orderId(), ex.getMessage());
        }
    }
}
