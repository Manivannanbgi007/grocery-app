package com.grocery.delivery.service.impl;

import com.grocery.delivery.assignment.DeliveryAssignmentService;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.entity.DeliveryZone;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import com.grocery.delivery.repository.DeliveryRepository;
import com.grocery.delivery.repository.DeliveryZoneRepository;
import com.grocery.delivery.service.DeliverySagaService;
import com.grocery.delivery.service.ProcessedEventService;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderReadyForPickup;
import com.grocery.events.user.UserRegistered;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliverySagaServiceImpl implements DeliverySagaService {

    private static final String DELIVERY_PARTNER = "DELIVERY_PARTNER";

    private final ProcessedEventService processedEventService;
    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryZoneRepository zoneRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentService assignmentService;

    @Override
    @Transactional
    public void handleUserRegistered(UserRegistered event) {
        if (!DELIVERY_PARTNER.equals(event.getRole())
                || !processedEventService.markAsProcessed(event.getEventId(), "UserRegistered")
                || partnerRepository.existsByUserId(event.getUserId())) {
            return;
        }
        partnerRepository.save(DeliveryPartner.register(event.getUserId(), event.getFullName(), event.getPhone()));
        log.info("Delivery partner profile created for user id={} (KYC not submitted yet)", event.getUserId());
    }

    @Override
    @Transactional
    public void handleOrderReadyForPickup(OrderReadyForPickup event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "OrderReadyForPickup")
                || deliveryRepository.findByOrderId(event.getOrderId()).isPresent()) {
            return;
        }
        // The delivery belongs to the zone of the STORE (where it is picked up)
        Long zoneId = zoneRepository.findActiveCovering(event.getStorePincode()).stream()
                .findFirst().map(DeliveryZone::getId).orElse(null);

        Delivery delivery = Delivery.forOrder(event.getOrderId(), event.getCustomerUserId(), event.getStoreId(), zoneId);
        delivery.setPickup(event.getStoreName(), event.getStorePhone(), event.getStoreAddress(), event.getStorePincode());
        delivery.setDrop(event.getDeliveryContactName(), event.getDeliveryContactPhone(), event.getDeliveryAddress(), event.getDeliveryPincode());
        delivery.setPayment(event.getPaymentMethod(), new BigDecimal(event.getAmountToCollect()));
        // null in events from before the coordinates existed, or when the store / address has none
        delivery.setCoordinates(decimal(event.getStoreLatitude()), decimal(event.getStoreLongitude()),
                decimal(event.getDeliveryLatitude()), decimal(event.getDeliveryLongitude()));
        deliveryRepository.save(delivery);
        log.info("New delivery for order id={} in zone id={}", event.getOrderId(), zoneId);

        assignmentService.tryAssign(delivery);
    }

    @Override
    @Transactional
    public void handleOrderCancelled(OrderCancelled event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "OrderCancelled")) {
            return;
        }
        Optional<Delivery> found = deliveryRepository.findByOrderId(event.getOrderId());
        if (found.isEmpty()) {
            return;   // most orders are cancelled before they are ready for pickup: no delivery yet
        }
        Delivery delivery = found.get();
        Long partnerUserId = delivery.getPartnerUserId();
        if (delivery.canBeCancelled()) {
            delivery.cancel();
            log.info("Delivery of order id={} cancelled", event.getOrderId());
            if (partnerUserId != null) {
                deliveryRepository.flush();
                partnerRepository.findByUserId(partnerUserId).ifPresent(assignmentService::offerWaitingDeliveryTo);
            }
        } else if (delivery.getStatus().canMoveTo(DeliveryStatus.FAILED)) {
            delivery.failBecauseOrderWasCancelled();
            log.warn("Order id={} was cancelled after pickup: partner id={} must return the items", event.getOrderId(), partnerUserId);
        }
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
