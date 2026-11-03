package com.grocery.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * cart-service's COPY of the platform's pricing settings (owner: platform-service). ONE row, id 1.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only
@Entity
@Table(name = "pricing_settings")
public class PricingSettings extends BaseEntity {

    public static final long SINGLE_ROW_ID = 1L;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "free_delivery_from", nullable = false, precision = 10, scale = 2)
    private BigDecimal freeDeliveryFrom;

    @Column(name = "minimum_order_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumOrderValue;

    public void update(BigDecimal deliveryFee, BigDecimal freeDeliveryFrom, BigDecimal minimumOrderValue) {
        this.deliveryFee = deliveryFee;
        this.freeDeliveryFrom = freeDeliveryFrom;
        this.minimumOrderValue = minimumOrderValue;
    }
}
