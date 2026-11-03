package com.grocery.delivery.service.impl;

import com.grocery.delivery.dto.request.DeliveryRatingRequest;
import com.grocery.delivery.dto.response.DeliveryRatingResponse;
import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.exception.ResourceNotFoundException;
import com.grocery.delivery.repository.DeliveryPartnerRepository;
import com.grocery.delivery.repository.DeliveryRepository;
import com.grocery.delivery.service.DeliveryRatingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryRatingServiceImpl implements DeliveryRatingService {

    private final DeliveryRepository deliveryRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final Clock clock;

    @Override
    @Transactional
    public DeliveryRatingResponse rateDelivery(Long customerUserId, DeliveryRatingRequest request) {
        Delivery delivery = findMyDelivery(customerUserId, request.orderId());
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        delivery.rate(request.rating(), comment, Instant.now(clock));

        DeliveryPartner partner = findPartner(delivery.getPartnerUserId());
        partner.addRating(request.rating());
        log.info("Delivery of order id={} rated {} stars (partner id={} now {})", request.orderId(), request.rating(),
                partner.getUserId(), partner.getRatingAverage());
        return toResponse(delivery, partner);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryRatingResponse getMyRating(Long customerUserId, Long orderId) {
        Delivery delivery = findMyDelivery(customerUserId, orderId);
        DeliveryPartner partner = delivery.getPartnerUserId() == null ? null : findPartner(delivery.getPartnerUserId());
        return toResponse(delivery, partner);
    }

    /** Another customer's delivery looks like it does not exist. */
    private Delivery findMyDelivery(Long customerUserId, Long orderId) {
        return deliveryRepository.findByOrderIdAndCustomerUserId(orderId, customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("No delivery found for order " + orderId));
    }

    private DeliveryPartner findPartner(Long partnerUserId) {
        return partnerRepository.findByUserId(partnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Delivery partner", partnerUserId));
    }

    private DeliveryRatingResponse toResponse(Delivery delivery, DeliveryPartner partner) {
        return new DeliveryRatingResponse(delivery.getOrderId(), partner == null ? null : partner.getFullName(),
                delivery.getCustomerRating(), delivery.getCustomerComment(), delivery.getRatedAt());
    }
}
