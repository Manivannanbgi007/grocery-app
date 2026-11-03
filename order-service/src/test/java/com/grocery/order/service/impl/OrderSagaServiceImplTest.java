package com.grocery.order.service.impl;

import com.grocery.events.delivery.DeliveryCompleted;
import com.grocery.events.delivery.DeliveryFailed;
import com.grocery.events.delivery.DeliveryPickedUp;
import com.grocery.events.inventory.StockReservationFailed;
import com.grocery.events.inventory.UnavailableItem;
import com.grocery.events.order.OrderConfirmed;
import com.grocery.events.order.OrderDelivered;
import com.grocery.events.order.OrderDeliveryFailed;
import com.grocery.events.order.OrderOutForDelivery;
import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.PaymentFailed;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.mapper.OrderEventMapper;
import com.grocery.order.messaging.OutboxEventPublisher;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.service.OrderCanceller;
import com.grocery.order.service.ProcessedEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.grocery.order.support.TestOrders.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSagaServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private ProcessedEventService processedEventService;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private OrderCanceller orderCanceller;

    private OrderSagaServiceImpl sagaService;

    @BeforeEach
    void setUp() {
        sagaService = new OrderSagaServiceImpl(orderRepository, processedEventService, new OrderEventMapper(),
                outboxEventPublisher, orderCanceller);
    }

    @Test
    void paymentConfirmed_confirmsAPendingOrder_andPublishesOrderConfirmed() {
        Order order = pendingOrder(PaymentMethod.COD);
        order.setId(10L);
        when(processedEventService.markAsProcessed(anyString(), eq("PaymentConfirmed"))).thenReturn(true);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));

        sagaService.handlePaymentConfirmed(paymentConfirmed(10L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.CASH_ON_DELIVERY);
        assertThat(order.getPaymentId()).isEqualTo(1L);   // the invoice names this payment
        verify(outboxEventPublisher).publish(eq("order"), eq(10L), any(OrderConfirmed.class));
    }

    @Test
    void paymentConfirmed_forAnOrderTheCustomerAlreadyCancelled_isIgnored() {
        Order order = pendingOrder(PaymentMethod.ONLINE);
        order.setId(11L);
        order.cancel("Changed my mind", ChangedBy.CUSTOMER);
        when(processedEventService.markAsProcessed(anyString(), eq("PaymentConfirmed"))).thenReturn(true);
        when(orderRepository.findById(11L)).thenReturn(Optional.of(order));

        sagaService.handlePaymentConfirmed(paymentConfirmed(11L));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);   // payment-service refunds it on OrderCancelled
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void duplicateEvent_isSkipped() {
        when(processedEventService.markAsProcessed(anyString(), eq("PaymentConfirmed"))).thenReturn(false);

        sagaService.handlePaymentConfirmed(paymentConfirmed(10L));

        verifyNoInteractions(orderRepository, outboxEventPublisher);
    }

    @Test
    void stockReservationFailed_cancelsTheOrder_namingTheMissingProducts() {
        Order order = pendingOrder(PaymentMethod.COD);
        order.setId(12L);
        when(processedEventService.markAsProcessed(anyString(), eq("StockReservationFailed"))).thenReturn(true);
        when(orderRepository.findById(12L)).thenReturn(Optional.of(order));

        sagaService.handleStockReservationFailed(StockReservationFailed.newBuilder()
                .setEventId("e1").setOccurredAt(Instant.now()).setOrderId(12L).setStoreId(1L)
                .setReason("Not enough stock")
                .setItems(List.of(new UnavailableItem(7L, 1, 0), new UnavailableItem(1L, 2, 1)))
                .build());

        verify(orderCanceller).cancel(order, "Not enough stock: Amul Butter (sold out), Tomato (1 left)", ChangedBy.SYSTEM);
    }

    @Test
    void paymentFailed_marksThePayment_andCancelsTheOrder() {
        Order order = pendingOrder(PaymentMethod.ONLINE);
        order.setId(13L);
        when(processedEventService.markAsProcessed(anyString(), eq("PaymentFailed"))).thenReturn(true);
        when(orderRepository.findById(13L)).thenReturn(Optional.of(order));

        sagaService.handlePaymentFailed(PaymentFailed.newBuilder()
                .setEventId("e2").setOccurredAt(Instant.now()).setPaymentId(5L).setOrderId(13L).setCustomerUserId(4L)
                .setReason("Card declined")
                .build());

        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.FAILED);
        verify(orderCanceller).cancel(order, "Payment failed: Card declined", ChangedBy.SYSTEM);
        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    @Test
    void deliveryEvents_moveTheOrderFromPickupToDelivered() {
        Order order = readyForPickupOrder(14L);
        when(processedEventService.markAsProcessed(anyString(), anyString())).thenReturn(true);
        when(orderRepository.findById(14L)).thenReturn(Optional.of(order));

        sagaService.handleDeliveryPickedUp(DeliveryPickedUp.newBuilder()
                .setEventId("dp-14").setOccurredAt(Instant.now()).setDeliveryId(1L).setOrderId(14L)
                .setCustomerUserId(4L).setStoreId(1L).setPartnerUserId(5L)
                .build());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.OUT_FOR_DELIVERY);
        verify(outboxEventPublisher).publish(eq("order"), eq(14L), any(OrderOutForDelivery.class));

        sagaService.handleDeliveryCompleted(DeliveryCompleted.newBuilder()
                .setEventId("dc-14").setOccurredAt(Instant.parse("2026-10-26T12:00:00Z")).setDeliveryId(1L).setOrderId(14L)
                .setCustomerUserId(4L).setStoreId(1L).setPartnerUserId(5L).setAmountCollected("158.00")
                .build());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERED);
        assertThat(order.getDeliveredAt()).isEqualTo(Instant.parse("2026-10-26T12:00:00Z"));
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.PAID);
        verify(outboxEventPublisher).publish(eq("order"), eq(14L), any(OrderDelivered.class));
    }

    @Test
    void deliveryFailed_endsTheOrder_andPublishesOrderDeliveryFailed() {
        Order order = readyForPickupOrder(15L);
        order.markOutForDelivery(ChangedBy.DELIVERY_PARTNER);
        when(processedEventService.markAsProcessed(anyString(), eq("DeliveryFailed"))).thenReturn(true);
        when(orderRepository.findById(15L)).thenReturn(Optional.of(order));

        sagaService.handleDeliveryFailed(DeliveryFailed.newBuilder()
                .setEventId("df-15").setOccurredAt(Instant.now()).setDeliveryId(2L).setOrderId(15L)
                .setCustomerUserId(4L).setStoreId(1L).setPartnerUserId(5L)
                .setIssue("CUSTOMER_UNAVAILABLE").setNote("Phone switched off")
                .build());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.DELIVERY_FAILED);
        assertThat(order.getStatusReason()).isEqualTo("Delivery failed: customer unavailable (Phone switched off)");
        assertThat(order.getPaymentStatus()).isEqualTo(OrderPaymentStatus.CANCELLED);
        verify(outboxEventPublisher).publish(eq("order"), eq(15L), any(OrderDeliveryFailed.class));
    }

    private Order readyForPickupOrder(Long id) {
        Order order = pendingOrder(PaymentMethod.COD);
        order.setId(id);
        order.confirm(OrderPaymentStatus.CASH_ON_DELIVERY);
        order.accept();
        order.pack();
        order.markReadyForPickup();
        return order;
    }

    private PaymentConfirmed paymentConfirmed(Long orderId) {
        return PaymentConfirmed.newBuilder()
                .setEventId("pc-" + orderId).setOccurredAt(Instant.now())
                .setPaymentId(1L).setOrderId(orderId).setCustomerUserId(4L)
                .setAmount("158.00").setPaymentMethod("COD")
                .build();
    }
}
