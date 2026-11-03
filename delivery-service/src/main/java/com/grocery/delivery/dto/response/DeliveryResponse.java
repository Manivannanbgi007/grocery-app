package com.grocery.delivery.dto.response;

import com.grocery.delivery.dto.common.GeoPoint;
import com.grocery.delivery.enums.DeliveryIssue;
import com.grocery.delivery.enums.DeliveryStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A delivery task with both addresses (what the partner app shows).
 *
 * @param amountToCollect  cash to collect at the door (COD), 0 for paid orders
 * @param storeLocation    where the store is, for the map (null when unknown)
 * @param customerLocation where the customer's address is, for the map (null when unknown)
 */
public record DeliveryResponse(
        Long id,
        Long orderId,
        DeliveryStatus status,
        String storeName,
        String storePhone,
        String storeAddress,
        String customerName,
        String customerPhone,
        String customerAddress,
        String paymentMethod,
        BigDecimal amountToCollect,
        Long partnerUserId,
        DeliveryIssue issue,
        String issueNote,
        String proofPhotoUrl,
        BigDecimal partnerPayout,
        Integer customerRating,
        String customerComment,
        Instant assignedAt,
        Instant pickedUpAt,
        Instant deliveredAt,
        Instant createdAt,
        GeoPoint storeLocation,
        GeoPoint customerLocation) {
}
