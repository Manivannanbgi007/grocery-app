package com.grocery.mcpserver.tool.result;

/**
 * The panels' codes as short texts, with the same words as the vendor, delivery and admin panels.
 */
final class PanelTexts {

    private PanelTexts() {
    }

    static String storeStatus(String status) {
        return switch (status) {
            case "PENDING_APPROVAL" -> "Waiting for an admin's approval";
            case "ACTIVE" -> "Approved";
            case "REJECTED" -> "Rejected by an admin";
            case "DISABLED" -> "Disabled by an admin";
            default -> status;
        };
    }

    static String kycStatus(String status) {
        return switch (status) {
            case "NOT_SUBMITTED" -> "Not sent yet";
            case "PENDING" -> "Sent, waiting for an admin's review";
            case "APPROVED" -> "Approved";
            case "REJECTED" -> "Rejected";
            default -> status;
        };
    }

    static String deliveryStep(String status) {
        return switch (status) {
            case "WAITING_FOR_PARTNER" -> "Waiting for a partner";
            case "ASSIGNED" -> "Given to the partner, not accepted yet";
            case "ACCEPTED" -> "Accepted, going to the store";
            case "PICKED_UP" -> "Picked up at the store";
            case "OUT_FOR_DELIVERY" -> "On the way to the customer";
            case "DELIVERED" -> "Delivered";
            case "FAILED" -> "Delivery failed";
            case "RETURNED" -> "Returned to the store";
            case "CANCELLED" -> "Cancelled";
            default -> status;
        };
    }

    static String vehicle(String type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case "BICYCLE" -> "Bicycle";
            case "SCOOTER" -> "Scooter";
            case "MOTORBIKE" -> "Motorbike";
            default -> type;
        };
    }
}
