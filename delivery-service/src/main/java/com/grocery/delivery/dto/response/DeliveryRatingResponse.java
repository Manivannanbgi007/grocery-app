package com.grocery.delivery.dto.response;

import java.time.Instant;

/**
 * @param rating null while the customer has not rated the delivery
 */
public record DeliveryRatingResponse(
        Long orderId,
        String partnerName,
        Integer rating,
        String comment,
        Instant ratedAt) {
}
