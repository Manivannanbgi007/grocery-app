package com.grocery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A COPY of the store's name and address at the time of the order (shown on the order and the invoice,
 * and used by the delivery partner for the pickup), and of the platform commission that applies to it.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class StoreDetails {

    @Column(name = "store_name", nullable = false, length = 100)
    private String name;

    @Column(name = "store_phone", nullable = false, length = 15)
    private String phone;

    /** One line, e.g. "45, 100 Feet Road, Indiranagar, Bengaluru 560038". */
    @Column(name = "store_address", nullable = false, length = 400)
    private String address;

    @Column(name = "store_pincode", nullable = false, length = 6)
    private String pincode;

    /** The platform's commission on this order, in percent (the store's rate when the order was placed). */
    @Column(name = "commission_percentage", precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    /** Where the store is (null when the vendor did not set it): the pickup point on the delivery tracking map. */
    @Column(name = "store_latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "store_longitude", precision = 9, scale = 6)
    private BigDecimal longitude;
}
