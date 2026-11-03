package com.grocery.delivery.entity;

import com.grocery.delivery.enums.DeliveryIssue;
import com.grocery.delivery.enums.DeliveryStatus;
import com.grocery.delivery.exception.BusinessException;
import com.grocery.delivery.exception.ForbiddenException;
import com.grocery.delivery.tracking.DeliveryStatusListener;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Taking one order from the store to the customer (DDD aggregate root) and its STATE MACHINE
 * (see {@link DeliveryStatus}). Created from OrderReadyForPickup, which carries both addresses.
 *
 * Every partner action checks that the delivery belongs to that partner and that the status allows it.
 * Every status change also reaches the customer's live tracking (see DeliveryStatusListener).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use Delivery.forOrder(...)
@Entity
@EntityListeners(DeliveryStatusListener.class)
@Table(name = "deliveries")
public class Delivery extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private Long orderId;

    @Column(name = "customer_user_id", nullable = false, updatable = false)
    private Long customerUserId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    /** The zone of the store; null when no zone covers the store (an admin assigns by hand). */
    @Column(name = "zone_id")
    private Long zoneId;

    // ----- pickup (the store) -----
    @Column(name = "store_name", nullable = false, length = 100)
    private String storeName;

    @Column(name = "store_phone", nullable = false, length = 15)
    private String storePhone;

    @Column(name = "store_address", nullable = false, length = 400)
    private String storeAddress;

    @Column(name = "store_pincode", nullable = false, length = 6)
    private String storePincode;

    /** Where the store is (null when unknown): the pickup marker on the tracking map. */
    @Column(name = "pickup_latitude", precision = 9, scale = 6)
    private BigDecimal pickupLatitude;

    @Column(name = "pickup_longitude", precision = 9, scale = 6)
    private BigDecimal pickupLongitude;

    // ----- drop (the customer) -----
    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(name = "customer_phone", nullable = false, length = 15)
    private String customerPhone;

    @Column(name = "customer_address", nullable = false, length = 400)
    private String customerAddress;

    @Column(name = "customer_pincode", nullable = false, length = 6)
    private String customerPincode;

    /** Where the customer's address is (null when unknown): the destination marker on the tracking map. */
    @Column(name = "drop_latitude", precision = 9, scale = 6)
    private BigDecimal dropLatitude;

    @Column(name = "drop_longitude", precision = 9, scale = 6)
    private BigDecimal dropLongitude;

    @Column(name = "payment_method", nullable = false, length = 10)
    private String paymentMethod;

    /** Cash the partner collects (COD), 0 for paid orders. */
    @Column(name = "amount_to_collect", nullable = false, precision = 10, scale = 2)
    private BigDecimal amountToCollect;

    // ----- the delivery itself -----
    @Column(name = "partner_user_id")
    private Long partnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DeliveryStatus status;

    /** Partners who rejected this delivery: it is not offered to them again. */
    @ElementCollection
    @CollectionTable(name = "delivery_rejections", joinColumns = @JoinColumn(name = "delivery_id"))
    @Column(name = "partner_user_id", nullable = false)
    private Set<Long> rejectedByPartnerIds = new HashSet<>();

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DeliveryIssue issue;

    @Column(name = "issue_note", length = 255)
    private String issueNote;

    @Column(name = "proof_photo_url", length = 500)
    private String proofPhotoUrl;

    /** What the partner earned for this delivery (set when delivered). */
    @Column(name = "partner_payout", precision = 10, scale = 2)
    private BigDecimal partnerPayout;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** 1 to 5 stars from the customer, after delivery. */
    @Column(name = "customer_rating")
    private Integer customerRating;

    @Column(name = "customer_comment", length = 500)
    private String customerComment;

    @Column(name = "rated_at")
    private Instant ratedAt;

    /** The status when this object was loaded or last reported: lets DeliveryStatusListener see real changes only. */
    @Transient
    private DeliveryStatus reportedStatus;

    public static Delivery forOrder(Long orderId, Long customerUserId, Long storeId, Long zoneId) {
        Delivery delivery = new Delivery();
        delivery.orderId = orderId;
        delivery.customerUserId = customerUserId;
        delivery.storeId = storeId;
        delivery.zoneId = zoneId;
        delivery.status = DeliveryStatus.WAITING_FOR_PARTNER;
        return delivery;
    }

    public void setPickup(String storeName, String storePhone, String storeAddress, String storePincode) {
        this.storeName = storeName;
        this.storePhone = storePhone;
        this.storeAddress = storeAddress;
        this.storePincode = storePincode;
    }

    public void setDrop(String customerName, String customerPhone, String customerAddress, String customerPincode) {
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.customerAddress = customerAddress;
        this.customerPincode = customerPincode;
    }

    public void setPayment(String paymentMethod, BigDecimal amountToCollect) {
        this.paymentMethod = paymentMethod;
        this.amountToCollect = amountToCollect;
    }

    /** Where the store and the customer are; any value may be null (not every store or address has coordinates). */
    public void setCoordinates(BigDecimal pickupLatitude, BigDecimal pickupLongitude,
                               BigDecimal dropLatitude, BigDecimal dropLongitude) {
        this.pickupLatitude = pickupLatitude;
        this.pickupLongitude = pickupLongitude;
        this.dropLatitude = dropLatitude;
        this.dropLongitude = dropLongitude;
    }

    /**
     * True once for each new status (then false until the status changes again). DeliveryStatusListener asks this
     * after every save, so the live tracking hears about status changes only - not about a rating or a photo.
     */
    public boolean takeStatusChange() {
        boolean changed = status != reportedStatus;
        reportedStatus = status;
        return changed;
    }

    @PostLoad
    void rememberLoadedStatus() {
        this.reportedStatus = status;
    }

    public boolean wasRejectedBy(Long partnerUserId) {
        return rejectedByPartnerIds.contains(partnerUserId);
    }

    // ----- assignment -----

    public void assignTo(Long partnerUserId, Instant now) {
        moveTo(DeliveryStatus.ASSIGNED);
        this.partnerUserId = partnerUserId;
        this.assignedAt = now;
    }

    public void accept(Long partnerUserId) {
        requirePartner(partnerUserId);
        moveTo(DeliveryStatus.ACCEPTED);
    }

    /** The partner does not take it: it goes back to waiting and is never offered to them again. */
    public void reject(Long partnerUserId) {
        requirePartner(partnerUserId);
        moveTo(DeliveryStatus.WAITING_FOR_PARTNER);
        rejectedByPartnerIds.add(partnerUserId);
        this.partnerUserId = null;
        this.assignedAt = null;
    }

    // ----- on the road -----

    public void pickUp(Long partnerUserId, Instant now) {
        requirePartner(partnerUserId);
        moveTo(DeliveryStatus.PICKED_UP);
        this.pickedUpAt = now;
    }

    public void startTrip(Long partnerUserId) {
        requirePartner(partnerUserId);
        moveTo(DeliveryStatus.OUT_FOR_DELIVERY);
    }

    public void deliver(Long partnerUserId, BigDecimal payout, Instant now) {
        requirePartner(partnerUserId);
        moveTo(DeliveryStatus.DELIVERED);
        this.partnerPayout = payout;
        this.deliveredAt = now;
    }

    public void reportIssue(Long partnerUserId, DeliveryIssue issue, String note) {
        requirePartner(partnerUserId);
        moveTo(DeliveryStatus.FAILED);
        this.issue = issue;
        this.issueNote = note;
    }

    /** After a failed delivery, the partner brought the items back to the store. */
    public void markReturned(Long partnerUserId) {
        requirePartner(partnerUserId);
        moveTo(DeliveryStatus.RETURNED);
    }

    /** The customer rates a delivered order once: 1 to 5 stars and an optional comment. */
    public void rate(int rating, String comment, Instant ratedAt) {
        if (status != DeliveryStatus.DELIVERED) {
            throw new BusinessException("Only a delivered order can be rated (this delivery is " + status + ")");
        }
        if (customerRating != null) {
            throw new BusinessException("You have already rated this delivery");
        }
        if (rating < 1 || rating > 5) {
            throw new BusinessException("The rating must be 1 to 5 stars");
        }
        this.customerRating = rating;
        this.customerComment = comment;
        this.ratedAt = ratedAt;
    }

    public void attachProof(Long partnerUserId, String photoUrl) {
        requirePartner(partnerUserId);
        if (status != DeliveryStatus.OUT_FOR_DELIVERY && status != DeliveryStatus.DELIVERED) {
            throw new BusinessException("A proof of delivery can only be added on the way or after delivering");
        }
        this.proofPhotoUrl = photoUrl;
    }

    /** The order was cancelled before the partner picked it up. */
    public boolean canBeCancelled() {
        return status.canMoveTo(DeliveryStatus.CANCELLED);
    }

    public void cancel() {
        moveTo(DeliveryStatus.CANCELLED);
    }

    /**
     * The order was cancelled while the partner already had the items (the events crossed):
     * the partner must take them back to the store.
     */
    public void failBecauseOrderWasCancelled() {
        moveTo(DeliveryStatus.FAILED);
        this.issue = DeliveryIssue.OTHER;
        this.issueNote = "The order was cancelled. Please take the items back to the store.";
    }

    private void requirePartner(Long userId) {
        if (partnerUserId == null || !partnerUserId.equals(userId)) {
            throw new ForbiddenException("This delivery is not assigned to you");
        }
    }

    private void moveTo(DeliveryStatus next) {
        if (!status.canMoveTo(next)) {
            throw new BusinessException("Delivery %d is %s, so it cannot become %s".formatted(getId(), status, next));
        }
        this.status = next;
    }
}
