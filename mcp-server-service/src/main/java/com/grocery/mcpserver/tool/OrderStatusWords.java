package com.grocery.mcpserver.tool;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static java.util.Map.entry;

/**
 * The words the model may use for the status filter of getVendorOrders and getAdminOrders, and the order status
 * each one means. Plain words are easier for a small model than codes like READY_FOR_PICKUP (the codes work too).
 */
final class OrderStatusWords {

    static final String ALLOWED = "new, pending, accepted, packed, ready, on the way, delivered, failed, cancelled or rejected";

    private static final Map<String, String> CODES = Map.ofEntries(
            entry("new", "CONFIRMED"),          // confirmed by the system, waiting for the store to accept it
            entry("confirmed", "CONFIRMED"),
            entry("pending", "PENDING"),        // just placed: stock or the online payment is not done yet
            entry("accepted", "ACCEPTED"),
            entry("packed", "PACKED"),
            entry("ready", "READY_FOR_PICKUP"),
            entry("ready for pickup", "READY_FOR_PICKUP"),
            entry("on the way", "OUT_FOR_DELIVERY"),
            entry("out for delivery", "OUT_FOR_DELIVERY"),
            entry("delivered", "DELIVERED"),
            entry("failed", "DELIVERY_FAILED"),
            entry("delivery failed", "DELIVERY_FAILED"),
            entry("cancelled", "CANCELLED"),
            entry("canceled", "CANCELLED"),
            entry("rejected", "REJECTED"));

    private OrderStatusWords() {
    }

    /** true for no filter: nothing, "all" or "any" */
    static boolean meansAll(String word) {
        return word == null || word.isBlank() || word.strip().equalsIgnoreCase("all") || word.strip().equalsIgnoreCase("any");
    }

    /** "new" -> CONFIRMED, "READY_FOR_PICKUP" -> READY_FOR_PICKUP; empty for a word that is not a status */
    static Optional<String> codeOf(String word) {
        String key = word.strip().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Optional.ofNullable(CODES.get(key));
    }
}
