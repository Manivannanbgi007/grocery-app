package com.grocery.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The store fields order-service needs, from store-service.
 *
 * @param status           PENDING_APPROVAL, ACTIVE, REJECTED or DISABLED
 * @param acceptingOrders  false = the vendor paused new orders
 * @param openNow          inside today's opening hours
 * @param deliveryPincodes PIN codes the store delivers to
 * @param latitude         where the store is (null when the vendor did not set it); for the delivery tracking map
 * @param longitude        see latitude
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoreInfo(
        Long id,
        String name,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String pincode,
        String status,
        boolean acceptingOrders,
        boolean openNow,
        List<String> deliveryPincodes,
        BigDecimal commissionPercentage,
        BigDecimal latitude,
        BigDecimal longitude) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean deliversTo(String customerPincode) {
        return deliveryPincodes != null && deliveryPincodes.contains(customerPincode);
    }

    /** "45, 100 Feet Road, Indiranagar, Bengaluru 560038" */
    public String oneLineAddress() {
        String street = Stream.of(addressLine1, addressLine2)
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(", "));
        return street + ", " + city + " " + pincode;
    }
}
