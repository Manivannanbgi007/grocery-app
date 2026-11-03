package com.grocery.delivery.entity;

import com.grocery.delivery.enums.KycStatus;
import com.grocery.delivery.enums.VehicleType;
import com.grocery.delivery.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * A delivery partner (DDD aggregate root). Created when a DELIVERY_PARTNER user registers
 * (UserRegistered); they then submit their KYC details and an admin approves them.
 *
 * Whether the partner is ONLINE is not stored here but in Redis (see PartnerPresenceService),
 * because it changes all the time and must expire on its own.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use DeliveryPartner.register(...)
@Entity
@Table(name = "delivery_partners")
public class DeliveryPartner extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    private Long userId;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, length = 15)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 20)
    private VehicleType vehicleType;

    @Column(name = "vehicle_number", length = 20)
    private String vehicleNumber;

    @Column(name = "driving_license_number", length = 30)
    private String drivingLicenseNumber;

    /** Where the partner works. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id")
    private DeliveryZone zone;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    /** Why the KYC was rejected. */
    @Column(name = "kyc_note", length = 255)
    private String kycNote;

    /** Ratings of this partner's deliveries: average = ratingTotal / ratingCount. */
    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    @Column(name = "rating_total", nullable = false)
    private int ratingTotal;

    public static DeliveryPartner register(Long userId, String fullName, String phone) {
        DeliveryPartner partner = new DeliveryPartner();
        partner.userId = userId;
        partner.fullName = fullName;
        partner.phone = phone;
        partner.kycStatus = KycStatus.NOT_SUBMITTED;
        return partner;
    }

    /** First submission, or a new one after a rejection. */
    public void submitKyc(VehicleType vehicleType, String vehicleNumber, String drivingLicenseNumber, DeliveryZone zone) {
        if (kycStatus == KycStatus.APPROVED) {
            throw new BusinessException("Your KYC is already approved. Contact support to change your details.");
        }
        this.vehicleType = vehicleType;
        this.vehicleNumber = vehicleNumber;
        this.drivingLicenseNumber = drivingLicenseNumber;
        this.zone = zone;
        this.kycStatus = KycStatus.PENDING;
        this.kycNote = null;
    }

    public void approveKyc() {
        requireKycStatus(KycStatus.PENDING);
        this.kycStatus = KycStatus.APPROVED;
    }

    public void rejectKyc(String reason) {
        requireKycStatus(KycStatus.PENDING);
        this.kycStatus = KycStatus.REJECTED;
        this.kycNote = reason;
    }

    public void addRating(int stars) {
        this.ratingCount++;
        this.ratingTotal += stars;
    }

    /** e.g. 4.5; null while nobody has rated this partner. */
    public BigDecimal getRatingAverage() {
        return ratingCount == 0 ? null
                : BigDecimal.valueOf(ratingTotal).divide(BigDecimal.valueOf(ratingCount), 1, RoundingMode.HALF_UP);
    }

    public boolean isApproved() {
        return kycStatus == KycStatus.APPROVED;
    }

    private void requireKycStatus(KycStatus expected) {
        if (kycStatus != expected) {
            throw new BusinessException("KYC of partner %d is %s, expected %s".formatted(userId, kycStatus, expected));
        }
    }
}
