package com.grocery.platform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The platform-wide settings. There is exactly ONE row (id 1), created by the V1 migration.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only
@Entity
@Table(name = "platform_settings")
public class PlatformSettings extends BaseEntity {

    public static final long SINGLE_ROW_ID = 1L;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    @Column(name = "free_delivery_from", nullable = false, precision = 10, scale = 2)
    private BigDecimal freeDeliveryFrom;

    @Column(name = "minimum_order_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal minimumOrderValue;

    @Column(name = "support_email", nullable = false, length = 120)
    private String supportEmail;

    @Column(name = "support_phone", nullable = false, length = 20)
    private String supportPhone;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;

    public void update(String currency, BigDecimal deliveryFee, BigDecimal freeDeliveryFrom, BigDecimal minimumOrderValue,
                       String supportEmail, String supportPhone, Long updatedByUserId) {
        this.currency = currency;
        this.deliveryFee = deliveryFee;
        this.freeDeliveryFrom = freeDeliveryFrom;
        this.minimumOrderValue = minimumOrderValue;
        this.supportEmail = supportEmail;
        this.supportPhone = supportPhone;
        this.updatedByUserId = updatedByUserId;
    }
}
