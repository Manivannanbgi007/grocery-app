package com.grocery.delivery.service;

import com.grocery.delivery.dto.request.DeliveryRatingRequest;
import com.grocery.delivery.dto.response.DeliveryRatingResponse;

/**
 * Customers rate the delivery of their orders ("rate the delivery experience").
 */
public interface DeliveryRatingService {

    /** Once per delivered order; the stars also count for the partner's average. */
    DeliveryRatingResponse rateDelivery(Long customerUserId, DeliveryRatingRequest request);

    DeliveryRatingResponse getMyRating(Long customerUserId, Long orderId);
}
