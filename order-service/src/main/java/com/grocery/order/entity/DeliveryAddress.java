package com.grocery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A COPY of the customer's address at the time of the order (DDD value object).
 * If the customer edits the address later, this order still goes to the address it was placed with.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class DeliveryAddress {

    @Column(name = "delivery_contact_name", nullable = false, length = 100)
    private String contactName;

    @Column(name = "delivery_contact_phone", nullable = false, length = 15)
    private String contactPhone;

    @Column(name = "delivery_address_line1", nullable = false, length = 150)
    private String addressLine1;

    @Column(name = "delivery_address_line2", length = 150)
    private String addressLine2;

    @Column(name = "delivery_landmark", length = 100)
    private String landmark;

    @Column(name = "delivery_city", nullable = false, length = 60)
    private String city;

    @Column(name = "delivery_state", nullable = false, length = 60)
    private String state;

    @Column(name = "delivery_pincode", nullable = false, length = 6)
    private String pincode;

    @Column(name = "delivery_latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "delivery_longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    /** "12, 5th Cross, Indiranagar, Near Metro, Bengaluru 560038" */
    public String oneLine() {
        String street = Stream.of(addressLine1, addressLine2, landmark)
                .filter(Objects::nonNull)
                .filter(part -> !part.isBlank())
                .collect(Collectors.joining(", "));
        return street + ", " + city + " " + pincode;
    }
}
