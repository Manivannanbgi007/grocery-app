package com.grocery.delivery.tracking;

import com.grocery.delivery.enums.DeliveryStatus;

/** A delivery got a new status (a Spring application event inside this service, not a Kafka event). */
public record DeliveryStatusChanged(Long deliveryId, Long orderId, DeliveryStatus status) {
}
