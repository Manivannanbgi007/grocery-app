package com.grocery.notification.message;

import com.grocery.events.delivery.DeliveryAssigned;
import com.grocery.events.delivery.PartnerKycReviewed;
import com.grocery.events.inventory.LowStockDetected;
import com.grocery.events.order.OrderCancelled;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderRejected;
import com.grocery.events.payment.PaymentFailed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.events.store.StoreStatusChanged;
import com.grocery.notification.enums.NotificationType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Every text the platform sends, in one place. Plain text: easy to read, easy to change.
 */
@Component
public class NotificationTemplates {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    // ----- users and stores -----

    public NotificationMessage welcome(String fullName) {
        return new NotificationMessage(NotificationType.WELCOME, "Welcome to Grocery, " + fullName + "!",
                "Your account is ready. Fresh groceries from stores near you are a few taps away.", null);
    }

    /** token: the reset token in plain text (decrypted from the event); the email is the only place it goes to. */
    public NotificationMessage passwordReset(String token, Instant expiresAt) {
        return new NotificationMessage(NotificationType.PASSWORD_RESET, "Reset your password",
                "Use this code to choose a new password: %s\nThe code works until %s (India time).\nIf you did not ask for it, you can ignore this email."
                        .formatted(token, TIME.format(expiresAt.atZone(INDIA))),
                null);
    }

    /** provider: as shown to users, e.g. "Google". */
    public NotificationMessage socialLoginLinked(String provider) {
        return new NotificationMessage(NotificationType.SOCIAL_LOGIN_LINKED, provider + " was connected to your account",
                "You can now log in with " + provider + ". If this was not you, reset your password with \"Forgot password\" "
                        + "on the login page and contact support.", null);
    }

    public NotificationMessage storeRegistered(Long storeId, String storeName) {
        return new NotificationMessage(NotificationType.STORE_REGISTERED, "We received your store " + storeName,
                "An admin will review " + storeName + " soon. We will let you know when it is approved.", storeId);
    }

    public NotificationMessage storeApproved(Long storeId, String storeName) {
        return new NotificationMessage(NotificationType.STORE_APPROVED, storeName + " is approved",
                "Open your vendor panel and add your products and stock. Customers can now find " + storeName + ".", storeId);
    }

    public NotificationMessage storeStatusChanged(StoreStatusChanged event, String storeName) {
        String reason = event.getReason() == null ? "" : " Reason: " + event.getReason();
        return new NotificationMessage(NotificationType.STORE_STATUS_CHANGED, storeName + " is now " + event.getStatus(),
                "The status of your store changed to " + event.getStatus() + "." + reason, event.getStoreId());
    }

    // ----- orders (customer) -----

    public NotificationMessage orderConfirmed(OrderConfirmed event) {
        String total = rupees(event.getTotalAmount());
        String payment = "COD".equals(event.getPaymentMethod()) ? "Please pay " + total + " in cash on delivery."
                : "We received your payment of " + total + ".";
        return order(NotificationType.ORDER_CONFIRMED, event.getOrderId(), "Order #%d confirmed",
                payment + " We will tell you when the store accepts it.");
    }

    public NotificationMessage orderAccepted(Long orderId) {
        return order(NotificationType.ORDER_ACCEPTED, orderId, "Order #%d accepted", "The store is preparing your order.");
    }

    public NotificationMessage orderRejected(OrderRejected event) {
        return order(NotificationType.ORDER_REJECTED, event.getOrderId(), "Order #%d could not be accepted",
                "The store rejected your order. Reason: " + event.getReason() + ". If you paid online, the money will be refunded.");
    }

    public NotificationMessage orderPacked(Long orderId) {
        return order(NotificationType.ORDER_PACKED, orderId, "Order #%d packed", "Your items are packed and wait for a delivery partner.");
    }

    public NotificationMessage orderOutForDelivery(Long orderId) {
        return order(NotificationType.ORDER_OUT_FOR_DELIVERY, orderId, "Order #%d is on the way", "Your order left the store.");
    }

    public NotificationMessage orderDelivered(OrderDelivered event) {
        return order(NotificationType.ORDER_DELIVERED, event.getOrderId(), "Order #%d delivered",
                "Enjoy your groceries! Order total: " + rupees(event.getTotalAmount()) + ".");
    }

    public NotificationMessage orderCancelled(OrderCancelled event) {
        return order(NotificationType.ORDER_CANCELLED, event.getOrderId(), "Order #%d cancelled", "Reason: " + event.getReason() + ".");
    }

    public NotificationMessage orderDeliveryFailed(OrderDeliveryFailed event) {
        return order(NotificationType.ORDER_DELIVERY_FAILED, event.getOrderId(), "Order #%d could not be delivered",
                event.getReason() + ". If you paid online, the money will be refunded. Nothing is owed for cash on delivery.");
    }

    public NotificationMessage deliveryPartnerAssigned(DeliveryAssigned event) {
        return order(NotificationType.DELIVERY_PARTNER_ASSIGNED, event.getOrderId(), "A delivery partner for order #%d",
                event.getPartnerName() + " (" + event.getPartnerPhone() + ") will bring your order.");
    }

    public NotificationMessage paymentFailed(PaymentFailed event) {
        return order(NotificationType.PAYMENT_FAILED, event.getOrderId(), "Payment for order #%d failed",
                event.getReason() + ". The order was cancelled; no money was taken.");
    }

    public NotificationMessage refundCompleted(RefundCompleted event) {
        return order(NotificationType.REFUND_COMPLETED, event.getOrderId(), "Refund for order #%d",
                "We refunded " + rupees(event.getAmount()) + " (reference " + event.getRefundReference()
                        + "). It can take a few days to show in your account.");
    }

    // ----- vendors -----

    public NotificationMessage newOrderForStore(OrderConfirmed event) {
        return order(NotificationType.NEW_ORDER, event.getOrderId(), "New order #%d",
                "A new order of " + rupees(event.getTotalAmount()) + " (" + event.getPaymentMethod() + ") is waiting. Accept or reject it in your store panel.");
    }

    public NotificationMessage orderCancelledForStore(OrderCancelled event) {
        return order(NotificationType.ORDER_CANCELLED, event.getOrderId(), "Order #%d was cancelled",
                "Cancelled by the " + event.getCancelledBy().toLowerCase() + ". Reason: " + event.getReason() + ". Do not pack it.");
    }

    public NotificationMessage orderDeliveryFailedForStore(OrderDeliveryFailed event) {
        return order(NotificationType.ORDER_DELIVERY_FAILED, event.getOrderId(), "Order #%d was not delivered",
                event.getReason() + ". The delivery partner brings the items back to the store.");
    }

    // ----- delivery partners -----

    public NotificationMessage partnerKycReviewed(PartnerKycReviewed event) {
        if ("APPROVED".equals(event.getStatus())) {
            return new NotificationMessage(NotificationType.PARTNER_KYC_REVIEWED, "Your KYC is approved",
                    "Welcome aboard, " + event.getFullName() + "! Go online in the delivery app to receive deliveries.", null);
        }
        return new NotificationMessage(NotificationType.PARTNER_KYC_REVIEWED, "Your KYC needs a correction",
                "Reason: " + event.getNote() + ". Please send your details again in the delivery app.", null);
    }

    public NotificationMessage newDelivery(DeliveryAssigned event) {
        return order(NotificationType.NEW_DELIVERY, event.getOrderId(), "New delivery: order #%d",
                "Open the delivery app to accept or reject it.");
    }

    // ----- vendors (stock) -----

    public NotificationMessage lowStock(LowStockDetected event) {
        return new NotificationMessage(NotificationType.LOW_STOCK, "Low stock: product #" + event.getProductId(),
                "Only %d left (alert at %d). Restock it in your inventory panel."
                        .formatted(event.getAvailableQuantity(), event.getLowStockThreshold()),
                event.getProductId());
    }

    private NotificationMessage order(NotificationType type, Long orderId, String titlePattern, String body) {
        return new NotificationMessage(type, titlePattern.formatted(orderId), body, orderId);
    }

    /**
     * An amount of an event (decimal text, "201.00") the way the website shows it: "₹201", "₹28.50", "₹1,25,000"
     * (Indian digit grouping, whole amounts without paise). Text that is not a number is shown as it came.
     */
    static String rupees(String amount) {
        BigDecimal value;
        try {
            value = new BigDecimal(amount.strip());
        } catch (NumberFormatException | NullPointerException ex) {
            return amount;
        }
        boolean whole = value.stripTrailingZeros().scale() <= 0;
        String number = value.setScale(whole ? 0 : 2, RoundingMode.HALF_UP).toPlainString();
        int dot = number.indexOf('.');
        String rupees = dot < 0 ? number : number.substring(0, dot);
        String paise = dot < 0 ? "" : number.substring(dot);
        return "₹" + indianGrouping(rupees) + paise;
    }

    /** "125000" -> "1,25,000": the last three digits, then groups of two. */
    private static String indianGrouping(String digits) {
        if (digits.length() <= 3) {
            return digits;
        }
        StringBuilder grouped = new StringBuilder(digits.substring(digits.length() - 3));
        for (int end = digits.length() - 3; end > 0; end -= 2) {
            grouped.insert(0, digits.substring(Math.max(0, end - 2), end) + ",");
        }
        return grouped.toString();
    }
}
