package com.grocery.delivery.service.impl;

import com.grocery.delivery.assignment.DeliveryAssignmentService;
import com.grocery.delivery.config.DeliveryProperties;
import com.grocery.delivery.dto.request.KycRequest;
import com.grocery.delivery.dto.response.AvailabilityResponse;
import com.grocery.delivery.dto.response.EarningsResponse;
import com.grocery.delivery.dto.response.PartnerResponse;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.entity.DeliveryZone;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.exception.BusinessException;
import com.grocery.delivery.exception.ResourceNotFoundException;
import com.grocery.delivery.mapper.DeliveryMapper;
import com.grocery.delivery.presence.PartnerPresenceService;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import com.grocery.delivery.repository.DeliveryRepository;
import com.grocery.delivery.repository.DeliveryZoneRepository;
import com.grocery.delivery.service.PartnerProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerProfileServiceImpl implements PartnerProfileService {

    private static final int MAX_EARNINGS_DAYS = 92;

    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryZoneRepository zoneRepository;
    private final DeliveryRepository deliveryRepository;
    private final PartnerPresenceService presenceService;
    private final DeliveryAssignmentService assignmentService;
    private final DeliveryMapper deliveryMapper;
    private final DeliveryProperties deliveryProperties;

    @Override
    @Transactional(readOnly = true)
    public PartnerResponse getMyProfile(Long partnerUserId) {
        return toResponse(findPartner(partnerUserId));
    }

    @Override
    @Transactional
    public PartnerResponse submitKyc(Long partnerUserId, KycRequest request) {
        DeliveryPartner partner = findPartner(partnerUserId);
        DeliveryZone zone = zoneRepository.findById(request.zoneId())
                .filter(DeliveryZone::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery zone", request.zoneId()));
        partner.submitKyc(request.vehicleType(), request.vehicleNumber(), request.drivingLicenseNumber().trim(), zone);
        log.info("Partner id={} submitted KYC for zone {}", partnerUserId, zone.getName());
        return toResponse(partner);
    }

    @Override
    @Transactional
    public AvailabilityResponse goOnline(Long partnerUserId) {
        DeliveryPartner partner = findPartner(partnerUserId);
        if (!partner.isApproved()) {
            throw new BusinessException("Only approved partners can go online. Your KYC is " + partner.getKycStatus());
        }
        Instant onlineUntil = presenceService.goOnline(partnerUserId);
        assignmentService.offerWaitingDeliveryTo(partner);   // a delivery may already be waiting in the zone
        return new AvailabilityResponse(true, onlineUntil);
    }

    @Override
    public AvailabilityResponse goOffline(Long partnerUserId) {
        presenceService.goOffline(partnerUserId);
        return new AvailabilityResponse(false, null);
    }

    @Override
    public AvailabilityResponse getAvailability(Long partnerUserId) {
        return presenceService.onlineUntil(partnerUserId)
                .map(onlineUntil -> new AvailabilityResponse(true, onlineUntil))
                .orElse(new AvailabilityResponse(false, null));
    }

    @Override
    @Transactional(readOnly = true)
    public EarningsResponse getEarnings(Long partnerUserId, LocalDate from, LocalDate to) {
        if (to.isBefore(from) || from.plusDays(MAX_EARNINGS_DAYS).isBefore(to)) {
            throw new BusinessException("Choose a period of 1 to " + MAX_EARNINGS_DAYS + " days (from <= to)");
        }
        ZoneId timeZone = deliveryProperties.timeZone();
        List<Delivery> delivered = deliveryRepository.findByPartnerUserIdAndStatusAndDeliveredAtBetween(partnerUserId,
                DeliveryStatus.DELIVERED, from.atStartOfDay(timeZone).toInstant(), to.plusDays(1).atStartOfDay(timeZone).toInstant());

        Map<LocalDate, List<Delivery>> byDay = delivered.stream()
                .collect(Collectors.groupingBy(delivery -> LocalDate.ofInstant(delivery.getDeliveredAt(), timeZone),
                        TreeMap::new, Collectors.toList()));
        List<EarningsResponse.DailyEarning> days = byDay.entrySet().stream()
                .map(day -> new EarningsResponse.DailyEarning(day.getKey(), day.getValue().size(), sumPayouts(day.getValue())))
                .toList();
        return new EarningsResponse(from, to, delivered.size(), sumPayouts(delivered), days);
    }

    private BigDecimal sumPayouts(List<Delivery> deliveries) {
        return deliveries.stream().map(Delivery::getPartnerPayout).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private PartnerResponse toResponse(DeliveryPartner partner) {
        return deliveryMapper.toResponse(partner, presenceService.isOnline(partner.getUserId()));
    }

    private DeliveryPartner findPartner(Long partnerUserId) {
        return partnerRepository.findByUserId(partnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No delivery partner profile for user " + partnerUserId));
    }
}
