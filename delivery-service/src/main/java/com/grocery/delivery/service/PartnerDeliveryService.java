package com.grocery.delivery.service;

import com.grocery.delivery.dto.common.PageResponse;
import com.grocery.delivery.dto.request.IssueRequest;
import com.grocery.delivery.dto.response.DeliveryResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

/**
 * The deliveries assigned to the logged-in partner, and every step they take with them.
 */
public interface PartnerDeliveryService {

    /** active = true: current tasks; false: finished ones (history). */
    PageResponse<DeliveryResponse> listMyDeliveries(Long partnerUserId, boolean active, Pageable pageable);

    DeliveryResponse getMyDelivery(Long partnerUserId, Long deliveryId);

    DeliveryResponse accept(Long partnerUserId, Long deliveryId);

    /** The delivery goes to another partner (or waits). */
    DeliveryResponse reject(Long partnerUserId, Long deliveryId);

    /** Collected at the store -> DeliveryPickedUp (the order is OUT_FOR_DELIVERY). */
    DeliveryResponse pickUp(Long partnerUserId, Long deliveryId);

    DeliveryResponse startTrip(Long partnerUserId, Long deliveryId);

    /** Handed to the customer -> DeliveryCompleted (the order is DELIVERED); the payout counts as earnings. */
    DeliveryResponse deliver(Long partnerUserId, Long deliveryId);

    /** Cannot deliver -> DeliveryFailed (the order is DELIVERY_FAILED). */
    DeliveryResponse reportIssue(Long partnerUserId, Long deliveryId, IssueRequest request);

    /** After a failed delivery: the items are back at the store. */
    DeliveryResponse markReturned(Long partnerUserId, Long deliveryId);

    DeliveryResponse uploadProof(Long partnerUserId, Long deliveryId, MultipartFile photo);
}
