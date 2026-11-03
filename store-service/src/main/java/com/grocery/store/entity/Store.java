package com.grocery.store.entity;

import com.grocery.store.enums.StoreStatus;
import com.grocery.store.exception.BusinessException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A grocery shop selling on the platform. Owned by exactly one vendor.
 *
 * The status rules (approve, reject, disable, enable) live HERE, on the entity,
 * so they are enforced no matter which service method changes the store.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "stores")
public class Store extends BaseEntity {

    /** The vendor (auth-service user id) who owns this store. One store per vendor. */
    @Column(name = "owner_user_id", nullable = false, unique = true)
    private Long ownerUserId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 15)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(name = "address_line1", nullable = false, length = 150)
    private String addressLine1;

    @Column(name = "address_line2", length = 150)
    private String addressLine2;

    @Column(nullable = false, length = 60)
    private String city;

    @Column(nullable = false, length = 60)
    private String state;

    @Column(nullable = false, length = 6)
    private String pincode;

    @Column(precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(precision = 9, scale = 6)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoreStatus status;

    /** Why the store was rejected or disabled (null otherwise). */
    @Column(name = "status_reason")
    private String statusReason;

    /** Platform commission on every order of this store, set by the super admin. */
    @Column(name = "commission_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercentage;

    /** The vendor can pause new orders (e.g. too busy) without closing the store. */
    @Column(name = "accepting_orders", nullable = false)
    private boolean acceptingOrders;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by_user_id")
    private Long approvedByUserId;

    @ElementCollection
    @CollectionTable(name = "store_opening_hours", joinColumns = @JoinColumn(name = "store_id"))
    private List<OpeningHours> openingHours = new ArrayList<>();

    /** PIN codes this store delivers to. */
    @ElementCollection
    @CollectionTable(name = "store_delivery_pincodes", joinColumns = @JoinColumn(name = "store_id"))
    @Column(name = "pincode", nullable = false, length = 6)
    private Set<String> deliveryPincodes = new HashSet<>();

    // ----- status rules -----

    public void approve(Long adminUserId, Instant approvedTime) {
        requireStatus(StoreStatus.PENDING_APPROVAL, "approved");
        this.status = StoreStatus.ACTIVE;
        this.statusReason = null;
        this.approvedAt = approvedTime;
        this.approvedByUserId = adminUserId;
    }

    public void reject(String reason) {
        requireStatus(StoreStatus.PENDING_APPROVAL, "rejected");
        this.status = StoreStatus.REJECTED;
        this.statusReason = reason;
    }

    public void disable(String reason) {
        requireStatus(StoreStatus.ACTIVE, "disabled");
        this.status = StoreStatus.DISABLED;
        this.statusReason = reason;
    }

    public void enable() {
        requireStatus(StoreStatus.DISABLED, "enabled");
        this.status = StoreStatus.ACTIVE;
        this.statusReason = null;
    }

    /** When the vendor fixes a rejected store, it goes back into the approval queue. */
    public void resubmitIfRejected() {
        if (status == StoreStatus.REJECTED) {
            this.status = StoreStatus.PENDING_APPROVAL;
            this.statusReason = null;
        }
    }

    public boolean isActive() {
        return status == StoreStatus.ACTIVE;
    }

    // ----- opening hours and delivery area -----

    /** @param localDateTime the current date and time in the store's time zone */
    public boolean isOpenAt(LocalDateTime localDateTime) {
        return openingHours.stream()
                .filter(hours -> hours.getDayOfWeek() == localDateTime.getDayOfWeek())
                .anyMatch(hours -> hours.isOpenAt(localDateTime.toLocalTime()));
    }

    public boolean deliversTo(String pincode) {
        return deliveryPincodes.contains(pincode);
    }

    public void replaceOpeningHours(Collection<OpeningHours> newOpeningHours) {
        openingHours.clear();
        openingHours.addAll(newOpeningHours);
    }

    public void replaceDeliveryPincodes(Collection<String> newPincodes) {
        deliveryPincodes.clear();
        deliveryPincodes.addAll(newPincodes);
    }

    private void requireStatus(StoreStatus expected, String action) {
        if (status != expected) {
            throw new BusinessException("Only a %s store can be %s (current status: %s)".formatted(expected, action, status));
        }
    }
}
