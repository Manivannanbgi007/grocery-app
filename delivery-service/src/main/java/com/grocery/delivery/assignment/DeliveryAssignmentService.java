package com.grocery.delivery.assignment;

import com.grocery.delivery.constant.KafkaTopics;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.enums.KycStatus;
import com.grocery.delivery.logging.Loggable;
import com.grocery.delivery.mapper.DeliveryEventMapper;
import com.grocery.delivery.messaging.OutboxEventPublisher;
import com.grocery.delivery.presence.PartnerPresenceService;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import com.grocery.delivery.repository.DeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Matches deliveries and partners. The rule is simple on purpose:
 *
 *   a delivery goes to the FIRST partner of its zone who is approved, online (Redis), not busy
 *   with another delivery, and has not rejected this delivery before.
 *
 * It runs when a delivery is created, when a partner rejects one, and when a partner becomes free
 * (goes online or finishes a delivery). A delivery nobody can take waits (WAITING_FOR_PARTNER);
 * an admin can also assign it by hand.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Loggable   // Finds a delivery partner: LoggingAspect logs the time of every call and warns when it is slow
public class DeliveryAssignmentService {

    private static final List<DeliveryStatus> BUSY = Arrays.stream(DeliveryStatus.values())
            .filter(DeliveryStatus::keepsPartnerBusy)
            .toList();

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final PartnerPresenceService presenceService;
    private final DeliveryEventMapper deliveryEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;

    /** @return true when a partner was found */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryAssign(Delivery delivery) {
        if (delivery.getStatus() != DeliveryStatus.WAITING_FOR_PARTNER || delivery.getZoneId() == null) {
            return false;
        }
        Optional<DeliveryPartner> freePartner = partnerRepository
                .findByZoneIdAndKycStatusOrderByIdAsc(delivery.getZoneId(), KycStatus.APPROVED).stream()
                .filter(partner -> !delivery.wasRejectedBy(partner.getUserId()))
                .filter(partner -> presenceService.isOnline(partner.getUserId()))
                .filter(partner -> isFree(partner.getUserId()))
                .findFirst();

        if (freePartner.isEmpty()) {
            log.info("Delivery of order id={}: no free partner online in zone id={} - waiting", delivery.getOrderId(), delivery.getZoneId());
            return false;
        }
        assign(delivery, freePartner.get());
        return true;
    }

    /** A partner just became free: give them the oldest waiting delivery of their zone. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void offerWaitingDeliveryTo(DeliveryPartner partner) {
        if (partner.getZone() == null || !partner.isApproved() || !presenceService.isOnline(partner.getUserId())
                || !isFree(partner.getUserId())) {
            return;
        }
        deliveryRepository.findByZoneIdAndStatusOrderByIdAsc(partner.getZone().getId(), DeliveryStatus.WAITING_FOR_PARTNER).stream()
                .filter(delivery -> !delivery.wasRejectedBy(partner.getUserId()))
                .findFirst()
                .ifPresent(delivery -> assign(delivery, partner));
    }

    /** Assigns without checks of zone or presence (used by the admin, who knows better). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void assign(Delivery delivery, DeliveryPartner partner) {
        delivery.assignTo(partner.getUserId(), Instant.now(clock));
        deliveryRepository.save(delivery);
        outboxEventPublisher.publish(KafkaTopics.DELIVERY_AGGREGATE, delivery.getOrderId(),
                deliveryEventMapper.toDeliveryAssigned(delivery, partner));
        log.info("Delivery of order id={} assigned to partner id={}", delivery.getOrderId(), partner.getUserId());
    }

    public boolean isFree(Long partnerUserId) {
        return !deliveryRepository.existsByPartnerUserIdAndStatusIn(partnerUserId, BUSY);
    }
}
