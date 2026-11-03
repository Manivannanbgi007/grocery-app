package com.grocery.order.entity;

import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.grocery.order.support.TestOrders.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The order state machine (plain JUnit).
 */
class OrderTest {

    @Test
    void happyPath_fromPlacedToDelivered_isRecordedInTheTimeline() {
        Order order = pendingOrder(PaymentMethod.COD);

        order.confirm(OrderPaymentStatus.CASH_ON_DELIVERY);
        order.accept();
        order.pack();
        order.markReadyForPickup();
        order.markOutForDelivery(ChangedBy.DELIVERY_PARTNER);
        order.markDelivered(ChangedBy.DELIVERY_PARTNER, Instant.parse("2026-10-26T12:00:00Z"));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getHistory()).extracting(OrderStatusHistory::getStatus).containsExactly(
                OrderStatus.PENDING, OrderStatus.CONFIRMED, OrderStatus.ACCEPTED, OrderStatus.PACKED,
                OrderStatus.READY_FOR_PICKUP, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);   // the cash was collected
    }

    @Test
    void storeCannotAcceptAnOrderBeforeItIsConfirmed() {
        Order order = pendingOrder(PaymentMethod.ONLINE);

        assertThatThrownBy(order::accept)
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("is PENDING, so it cannot become ACCEPTED");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void customerCanCancelUntilTheOrderLeavesTheStore() {
        Order packed = pendingOrder(PaymentMethod.COD);
        packed.confirm(OrderPaymentStatus.CASH_ON_DELIVERY);
        packed.accept();
        packed.pack();

        packed.cancel("Ordered by mistake", ChangedBy.CUSTOMER);

        assertThat(packed.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(packed.getStatusReason()).isEqualTo("Ordered by mistake");
        assertThat(packed.getCancelledBy()).isEqualTo(ChangedBy.CUSTOMER);
        assertThat(packed.getPaymentStatus()).isEqualTo(OrderPaymentStatus.CANCELLED);   // COD: nothing is owed now
    }

    @Test
    void cancellingAPaidOrder_keepsPaidUntilTheRefundArrives() {
        Order paid = pendingOrder(PaymentMethod.ONLINE);
        paid.confirm(OrderPaymentStatus.PAID);

        paid.cancel("Changed my mind", ChangedBy.CUSTOMER);
        assertThat(paid.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);

        paid.markRefunded();
        assertThat(paid.getPaymentStatus()).isEqualTo(OrderPaymentStatus.REFUNDED);
    }

    @Test
    void onlyAnOnlineOrderThatIsNotPaidYet_awaitsAnOnlinePayment() {
        assertThat(pendingOrder(PaymentMethod.ONLINE).awaitsOnlinePayment()).isTrue();
        assertThat(pendingOrder(PaymentMethod.COD).awaitsOnlinePayment()).isFalse();

        Order paid = pendingOrder(PaymentMethod.ONLINE);
        paid.confirm(OrderPaymentStatus.PAID);
        assertThat(paid.awaitsOnlinePayment()).isFalse();

        Order failed = pendingOrder(PaymentMethod.ONLINE);
        failed.markPaymentFailed();
        assertThat(failed.awaitsOnlinePayment()).isFalse();

        Order cancelled = pendingOrder(PaymentMethod.ONLINE);
        cancelled.cancel("Ordered by mistake", ChangedBy.CUSTOMER);
        assertThat(cancelled.awaitsOnlinePayment()).isFalse();
    }

    @Test
    void anOrderOnTheWay_canNoLongerBeCancelled() {
        Order onTheWay = pendingOrder(PaymentMethod.COD);
        onTheWay.confirm(OrderPaymentStatus.CASH_ON_DELIVERY);
        onTheWay.accept();
        onTheWay.pack();
        onTheWay.markReadyForPickup();
        onTheWay.markOutForDelivery(ChangedBy.DELIVERY_PARTNER);

        assertThat(onTheWay.isCancellable()).isFalse();
        assertThatThrownBy(() -> onTheWay.cancel("Too slow", ChangedBy.CUSTOMER))
                .isInstanceOf(BusinessException.class)
                .hasMessage("An order that is OUT_FOR_DELIVERY can no longer be cancelled");
    }

    @Test
    void failedDelivery_isFinal_andCodOwesNothing() {
        Order order = pendingOrder(PaymentMethod.COD);
        order.confirm(OrderPaymentStatus.CASH_ON_DELIVERY);
        order.accept();
        order.pack();
        order.markReadyForPickup();
        order.assignDeliveryPartner("Arjun Singh", "9000000005");
        order.markOutForDelivery(ChangedBy.DELIVERY_PARTNER);

        order.failDelivery("Delivery failed: customer unavailable");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.CANCELLED);
        assertThat(order.getDeliveryPartnerName()).isEqualTo("Arjun Singh");
        assertThat(order.getHistory().get(5).getNote()).isEqualTo("On the way to you with Arjun Singh");
    }

    @Test
    void storeRejection_keepsTheReason() {
        Order order = pendingOrder(PaymentMethod.ONLINE);
        order.confirm(OrderPaymentStatus.PAID);

        order.reject("Out of fresh tomatoes today");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getStatusReason()).isEqualTo("Out of fresh tomatoes today");
        assertThat(order.isCancellable()).isFalse();
    }
}
