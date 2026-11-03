package com.grocery.mcpserver.tool;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * What a notification is about, for "my notifications about my orders": each topic is a group of notification types
 * of notification-service (NotificationType). A type may be in two topics: "your order is out for delivery" is about
 * the order and about its delivery.
 */
enum NotificationTopic {

    /** everything about an order, also its delivery and its payment */
    ORDERS("orders", Set.of("order", "orders"), Set.of("ORDER_CONFIRMED", "NEW_ORDER", "ORDER_ACCEPTED", "ORDER_REJECTED",
            "ORDER_PACKED", "ORDER_OUT_FOR_DELIVERY", "ORDER_DELIVERED", "ORDER_CANCELLED", "ORDER_DELIVERY_FAILED",
            "DELIVERY_PARTNER_ASSIGNED", "PAYMENT_FAILED", "REFUND_COMPLETED")),
    DELIVERIES("deliveries", Set.of("delivery", "deliveries"), Set.of("NEW_DELIVERY", "DELIVERY_PARTNER_ASSIGNED",
            "ORDER_OUT_FOR_DELIVERY", "ORDER_DELIVERED", "ORDER_DELIVERY_FAILED")),
    PAYMENTS("payments", Set.of("payment", "payments", "refund", "refunds"), Set.of("PAYMENT_FAILED", "REFUND_COMPLETED")),
    STORE("store", Set.of("store", "stores"), Set.of("STORE_REGISTERED", "STORE_APPROVED", "STORE_STATUS_CHANGED")),
    ACCOUNT("account", Set.of("account", "login", "kyc"), Set.of("WELCOME", "PASSWORD_RESET", "SOCIAL_LOGIN_LINKED",
            "PARTNER_KYC_REVIEWED"));

    /** The topic words a tool call may use, for the INVALID_INPUT message. */
    static final String CHOICES = "orders, deliveries, payments, store or account";

    private final String label;
    private final Set<String> words;
    private final Set<String> types;

    NotificationTopic(String label, Set<String> words, Set<String> types) {
        this.label = label;
        this.words = words;
        this.types = types;
    }

    /** The topic of a word the model sends ("orders", "Payment", " refunds "), or empty for an unknown word. */
    static Optional<NotificationTopic> of(String word) {
        String key = word.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(topic -> topic.words.contains(key)).findFirst();
    }

    boolean covers(String notificationType) {
        return types.contains(notificationType);
    }

    String label() {
        return label;
    }
}
