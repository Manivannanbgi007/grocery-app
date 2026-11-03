package com.grocery.order.dto.response;

public record DeliveryAddressResponse(
        String contactName,
        String contactPhone,
        String addressLine1,
        String addressLine2,
        String landmark,
        String city,
        String state,
        String pincode) {
}
