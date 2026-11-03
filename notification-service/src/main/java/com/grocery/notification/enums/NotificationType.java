package com.grocery.notification.enums;

/**
 * What a notification is about. The comment says who receives it and which event creates it.
 */
public enum NotificationType {
    /** New user - UserRegistered */
    WELCOME,
    /** Email only (the user is not logged in) - PasswordResetRequested */
    PASSWORD_RESET,
    /** The user - SocialLoginLinked: a Google, Microsoft, Facebook or Apple account was connected (not you? reset the password) */
    SOCIAL_LOGIN_LINKED,
    /** Vendor - StoreRegistered */
    STORE_REGISTERED,
    /** Vendor - StoreApproved */
    STORE_APPROVED,
    /** Vendor - StoreStatusChanged (rejected, disabled, enabled) */
    STORE_STATUS_CHANGED,
    /** Customer - OrderConfirmed */
    ORDER_CONFIRMED,
    /** Vendor - OrderConfirmed: a new order to accept */
    NEW_ORDER,
    /** Customer - OrderAccepted */
    ORDER_ACCEPTED,
    /** Customer - OrderRejected */
    ORDER_REJECTED,
    /** Customer - OrderPacked */
    ORDER_PACKED,
    /** Customer - OrderOutForDelivery */
    ORDER_OUT_FOR_DELIVERY,
    /** Customer - OrderDelivered */
    ORDER_DELIVERED,
    /** Customer (and the vendor, unless the system cancelled it) - OrderCancelled */
    ORDER_CANCELLED,
    /** Customer and vendor - OrderDeliveryFailed */
    ORDER_DELIVERY_FAILED,
    /** Customer - DeliveryAssigned: who brings the order */
    DELIVERY_PARTNER_ASSIGNED,
    /** Delivery partner - DeliveryAssigned: a new job */
    NEW_DELIVERY,
    /** Delivery partner - PartnerKycReviewed: approved (can go online) or rejected (with the reason) */
    PARTNER_KYC_REVIEWED,
    /** Customer - PaymentFailed */
    PAYMENT_FAILED,
    /** Customer - RefundCompleted */
    REFUND_COMPLETED,
    /** Vendor - LowStockDetected */
    LOW_STOCK
}
