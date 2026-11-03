package com.grocery.notification.message;

import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.payment.RefundCompleted;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Amounts in the texts look like the website's prices: ₹, Indian digit grouping, whole amounts without paise. */
class NotificationTemplatesTest {

    private final NotificationTemplates templates = new NotificationTemplates();

    @Test
    void amountsAreRupeesLikeOnTheWebsite() {
        assertThat(NotificationTemplates.rupees("138.00")).isEqualTo("₹138");
        assertThat(NotificationTemplates.rupees("28.5")).isEqualTo("₹28.50");
        assertThat(NotificationTemplates.rupees("1250.50")).isEqualTo("₹1,250.50");
        assertThat(NotificationTemplates.rupees("125000")).isEqualTo("₹1,25,000");
        assertThat(NotificationTemplates.rupees("12345678.00")).isEqualTo("₹1,23,45,678");
        assertThat(NotificationTemplates.rupees("999")).isEqualTo("₹999");
        // Not a number: shown as it came, never an error
        assertThat(NotificationTemplates.rupees("n/a")).isEqualTo("n/a");
        assertThat(NotificationTemplates.rupees(null)).isNull();
    }

    @Test
    void theOrderAndRefundTextsShowRupees() {
        OrderConfirmed cashOrder = OrderConfirmed.newBuilder().setEventId("e1").setOccurredAt(Instant.now()).setOrderId(195L)
                .setCustomerUserId(4L).setStoreId(1L).setTotalAmount("138.00").setPaymentMethod("COD").build();
        assertThat(templates.orderConfirmed(cashOrder).body())
                .isEqualTo("Please pay ₹138 in cash on delivery. We will tell you when the store accepts it.");
        assertThat(templates.newOrderForStore(cashOrder).body())
                .isEqualTo("A new order of ₹138 (COD) is waiting. Accept or reject it in your store panel.");

        OrderDelivered delivered = OrderDelivered.newBuilder().setEventId("e2").setOccurredAt(Instant.now()).setOrderId(194L)
                .setCustomerUserId(4L).setStoreId(1L).setTotalAmount("150.00").setPaymentMethod("COD").build();
        assertThat(templates.orderDelivered(delivered).body()).isEqualTo("Enjoy your groceries! Order total: ₹150.");

        RefundCompleted refund = RefundCompleted.newBuilder().setEventId("e3").setOccurredAt(Instant.now()).setPaymentId(77L).setOrderId(193L)
                .setCustomerUserId(4L).setAmount("396.00").setRefundReference("rfnd_1").build();
        assertThat(templates.refundCompleted(refund).body()).startsWith("We refunded ₹396 (reference rfnd_1).");
    }
}
