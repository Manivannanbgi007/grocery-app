package com.grocery.delivery.service.impl;

import com.grocery.delivery.assignment.DeliveryAssignmentService;
import com.grocery.delivery.constant.KafkaTopics;
import com.grocery.delivery.dto.common.PageResponse;
import com.grocery.delivery.dto.request.ZoneRequest;
import com.grocery.delivery.dto.response.DeliveryResponse;
import com.grocery.delivery.dto.response.PartnerResponse;
import com.grocery.delivery.dto.response.ZoneResponse;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.entity.DeliveryZone;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.enums.KycStatus;
import com.grocery.delivery.exception.BusinessException;
import com.grocery.delivery.exception.DuplicateResourceException;
import com.grocery.delivery.exception.ResourceNotFoundException;
import com.grocery.delivery.mapper.DeliveryEventMapper;
import com.grocery.delivery.mapper.DeliveryMapper;
import com.grocery.delivery.messaging.OutboxEventPublisher;
import com.grocery.delivery.presence.PartnerPresenceService;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import com.grocery.delivery.repository.DeliveryRepository;
import com.grocery.delivery.repository.DeliveryZoneRepository;
import com.grocery.delivery.service.AdminDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDeliveryServiceImpl implements AdminDeliveryService {

    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryZoneRepository zoneRepository;
    private final DeliveryRepository deliveryRepository;
    private final DeliveryAssignmentService assignmentService;
    private final PartnerPresenceService presenceService;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryEventMapper deliveryEventMapper;
    private final OutboxEventPublisher outboxEventPublisher;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PartnerResponse> searchPartners(KycStatus kycStatus, Pageable pageable) {
        return PageResponse.from(partnerRepository.search(kycStatus, pageable)
                .map(partner -> deliveryMapper.toResponse(partner, presenceService.isOnline(partner.getUserId()))));
    }

    @Override
    @Transactional
    public PartnerResponse approveKyc(Long partnerUserId) {
        DeliveryPartner partner = findPartner(partnerUserId);
        partner.approveKyc();
        outboxEventPublisher.publish(KafkaTopics.DELIVERY_AGGREGATE, partnerUserId, deliveryEventMapper.toPartnerKycReviewed(partner));
        log.info("Partner id={} approved", partnerUserId);
        return deliveryMapper.toResponse(partner, presenceService.isOnline(partnerUserId));
    }

    @Override
    @Transactional
    public PartnerResponse rejectKyc(Long partnerUserId, String reason) {
        DeliveryPartner partner = findPartner(partnerUserId);
        partner.rejectKyc(reason.trim());
        outboxEventPublisher.publish(KafkaTopics.DELIVERY_AGGREGATE, partnerUserId, deliveryEventMapper.toPartnerKycReviewed(partner));
        log.info("Partner id={} rejected: {}", partnerUserId, reason);
        return deliveryMapper.toResponse(partner, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneResponse> listZones() {
        return zoneRepository.findAll(Sort.by("name")).stream().map(deliveryMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public ZoneResponse createZone(ZoneRequest request) {
        if (zoneRepository.existsByName(request.name().trim())) {
            throw new DuplicateResourceException("A zone named " + request.name().trim() + " already exists");
        }
        DeliveryZone zone = new DeliveryZone();
        copyZoneDetails(request, zone);
        return deliveryMapper.toResponse(zoneRepository.save(zone));
    }

    @Override
    @Transactional
    public ZoneResponse updateZone(Long zoneId, ZoneRequest request) {
        DeliveryZone zone = zoneRepository.findById(zoneId).orElseThrow(() -> new ResourceNotFoundException("Delivery zone", zoneId));
        if (!zone.getName().equals(request.name().trim()) && zoneRepository.existsByName(request.name().trim())) {
            throw new DuplicateResourceException("A zone named " + request.name().trim() + " already exists");
        }
        copyZoneDetails(request, zone);
        return deliveryMapper.toResponse(zone);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DeliveryResponse> searchDeliveries(DeliveryStatus status, Long partnerUserId, Pageable pageable) {
        return PageResponse.from(deliveryRepository.search(status, partnerUserId, pageable).map(deliveryMapper::toResponse));
    }

    @Override
    @Transactional
    public DeliveryResponse assignManually(Long deliveryId, Long partnerUserId) {
        Delivery delivery = deliveryRepository.findById(deliveryId).orElseThrow(() -> new ResourceNotFoundException("Delivery", deliveryId));
        DeliveryPartner partner = findPartner(partnerUserId);
        if (!partner.isApproved()) {
            throw new BusinessException("Partner " + partnerUserId + " is not approved");
        }
        if (!assignmentService.isFree(partnerUserId)) {
            throw new BusinessException("Partner " + partnerUserId + " is busy with another delivery");
        }
        assignmentService.assign(delivery, partner);
        return deliveryMapper.toResponse(delivery);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryResponse getDeliveryOfOrder(Long orderId) {
        return deliveryRepository.findByOrderId(orderId)
                .map(deliveryMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No delivery for order " + orderId));
    }

    private void copyZoneDetails(ZoneRequest request, DeliveryZone zone) {
        zone.setName(request.name().trim());
        zone.setCity(request.city().trim());
        zone.replacePincodes(request.pincodes());
        zone.setPartnerPayout(request.partnerPayout());
        zone.setActive(request.activeOrDefault());
    }

    private DeliveryPartner findPartner(Long partnerUserId) {
        return partnerRepository.findByUserId(partnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No delivery partner with user id " + partnerUserId));
    }
}
