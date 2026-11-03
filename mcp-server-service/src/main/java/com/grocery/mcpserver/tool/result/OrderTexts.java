package com.grocery.mcpserver.tool.result;

/**
 * Order codes as short sentences a customer understands (the same words as the website's order tracking).
 */
final class OrderTexts {

    private OrderTexts() {
    }

    static String status(String status) {
        return switch (status) {
            case "PENDING" -> "Placed, waiting for confirmation";
            case "CONFIRMED" -> "Confirmed, waiting for the store";
            case "ACCEPTED" -> "Accepted by the store";
            case "PACKED" -> "Packed by the store";
            case "READY_FOR_PICKUP" -> "Packed, waiting for a delivery partner";
            case "OUT_FOR_DELIVERY" -> "On the way";
            case "DELIVERED" -> "Delivered";
            case "DELIVERY_FAILED" -> "Delivery failed";
            case "CANCELLED" -> "Cancelled";
            case "REJECTED" -> "Rejected by the store";
            default -> status;
        };
    }

    /** ("ONLINE", "REFUNDED") -> "Online payment, refunded"; ("COD", "PENDING") -> "Cash on delivery, not paid yet" */
    static String payment(String method, String status) {
        String how = "COD".equals(method) ? "Cash on delivery" : "Online payment";
        String state = switch (status == null ? "" : status) {
            case "PAID" -> "paid";
            case "PENDING" -> "not paid yet";
            case "FAILED" -> "failed";
            case "REFUNDED" -> "refunded";
            case "CANCELLED" -> "cancelled";
            default -> status == null ? null : status.toLowerCase();
        };
        return state == null ? how : how + ", " + state;
    }
}
