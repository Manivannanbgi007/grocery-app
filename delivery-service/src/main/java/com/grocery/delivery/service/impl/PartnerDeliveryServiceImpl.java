package com.grocery.delivery.service.impl;

import com.grocery.delivery.assignment.DeliveryAssignmentService;
import com.grocery.delivery.constant.KafkaTopics;
import com.grocery.delivery.dto.common.PageResponse;
import com.grocery.delivery.dto.request.IssueRequest;
import com.grocery.delivery.dto.response.DeliveryResponse;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryZone;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.exception.ResourceNotFoundException;
import com.grocery.delivery.mapper.DeliveryEventMapper;
import com.grocery.delivery.mapper.DeliveryMapper;
import com.grocery.delivery.messaging.OutboxEventPublisher;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import com.grocery.delivery.repository.DeliveryRepository;
import com.grocery.delivery.repository.DeliveryZoneRepository;
import com.grocery.delivery.service.PartnerDeliveryService;
import com.grocery.delivery.storage.ProofPhotoStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerDeliveryServiceImpl implements PartnerDeliveryService {

    private static final List<DeliveryStatus> ACTIVE = Arrays.stream(DeliveryStatus.values())
            .filter(DeliveryStatus::keepsPartnerBusy).toList();
    private static final List<DeliveryStatus> FINISHED = List.of(DeliveryStatus.DELIVERED, DeliveryStatus.RETURNED);

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryZoneRepository zoneRepository;
    private final DeliveryAssignmentService assignmentService;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryEventMapper deliveryEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;
    private final ProofPhotoStorage proofPhotoStorage;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeliveryResponse> listMyDeliveries(Long partnerUserId, boolean active, Pageable pageable) {
        return PageResponse.from(deliveryRepository
                .findByPartnerUserIdAndStatusIn(partnerUserId, active ? ACTIVE : FINISHED, pageable)
                .map(deliveryMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryResponse getMyDelivery(Long partnerUserId, Long deliveryId) {
        return deliveryMapper.toResponse(findMyDelivery(partnerUserId, deliveryId));
    }

    @Override
    @Transactional
    public DeliveryResponse accept(Long partnerUserId, Long deliveryId) {
        Delivery delivery = findMyDelivery(partnerUserId, deliveryId);
        delivery.accept(partnerUserId);
        log.info("Partner id={} accepted the delivery of order id={}", partnerUserId, delivery.getOrderId());
        return deliveryMapper.toResponse(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse reject(Long partnerUserId, Long deliveryId) {
        Delivery delivery = findMyDelivery(partnerUserId, deliveryId);
        delivery.reject(partnerUserId);
        log.info("Partner id={} rejected the delivery of order id={}", partnerUserId, delivery.getOrderId());
        assignmentService.tryAssign(delivery);   // offer it to the next free partner
        return deliveryMapper.toResponse(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse pickUp(Long partnerUserId, Long deliveryId) {
        Delivery delivery = findMyDelivery(partnerUserId, deliveryId);
        delivery.pickUp(partnerUserId, Instant.now(clock));
        outboxEventPublisher.publish(KafkaTopics.DELIVERY_AGGREGATE, delivery.getOrderId(), deliveryEventMapper.toDeliveryPickedUp(delivery));
        return deliveryMapper.toResponse(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse startTrip(Long partnerUserId, Long deliveryId) {
        Delivery delivery = findMyDelivery(partnerUserId, deliveryId);
        delivery.startTrip(partnerUserId);
        return deliveryMapper.toResponse(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse deliver(Long partnerUserId, Long deliveryId) {
        Delivery delivery = findMyDelivery(partnerUserId, deliveryId);
        BigDecimal payout = delivery.getZoneId() == null ? BigDecimal.ZERO
                : zoneRepository.findById(delivery.getZoneId()).map(DeliveryZone::getPartnerPayout).orElse(BigDecimal.ZERO);
        delivery.deliver(partnerUserId, payout, Instant.now(clock));
        outboxEventPublisher.publish(KafkaTopics.DELIVERY_AGGREGATE, delivery.getOrderId(), deliveryEventMapper.toDeliveryCompleted(delivery));
        log.info("Partner id={} delivered order id={} (earned {})", partnerUserId, delivery.getOrderId(), payout);
        offerNextDelivery(partnerUserId);
        return deliveryMapper.toResponse(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse reportIssue(Long partnerUserId, Long deliveryId, IssueRequest request) {
        Delivery delivery = findMyDelivery(partnerUserId, deliveryId);
        delivery.reportIssue(partnerUserId, request.issue(), request.note());
        outboxEventPublisher.publish(KafkaTopics.DELIVERY_AGGREGATE, delivery.getOrderId(), deliveryEventMapper.toDeliveryFailed(delivery));
        log.info("Partner id={} could not deliver order id={}: {}", partnerUserId, delivery.getOrderId(), request.issue());
        return deliveryMapper.toResponse(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse markReturned(Long partnerUserId, Long deliveryId) {
        Delivery delivery = findMyDelivery(partnerUserId, deliveryId);
        delivery.markReturned(partnerUserId);
        offerNextDelivery(partnerUserId);
        return deliveryMapper.toResponse(delivery);
    }

    @Override
    @Transactional
    public DeliveryResponse uploadProof(Long partnerUserId, Long deliveryId, MultipartFile photo) {
        Delivery delivery = findMyDelivery(partnerUserId, deliveryId);
        delivery.attachProof(partnerUserId, proofPhotoStorage.store(deliveryId, photo));
        return deliveryMapper.toResponse(delivery);
    }

    /** The partner is free again: the oldest waiting delivery of their zone goes to them. */
    private void offerNextDelivery(Long partnerUserId) {
        deliveryRepository.flush();   // the finished delivery must count as finished in the "is the partner free?" query
        partnerRepository.findByUserId(partnerUserId).ifPresent(assignmentService::offerWaitingDeliveryTo);
    }

    /** Another partner's delivery looks like it does not exist. */
    private Delivery findMyDelivery(Long partnerUserId, Long deliveryId) {
        return deliveryRepository.findByIdAndPartnerUserId(deliveryId, partnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId));
    }
}
