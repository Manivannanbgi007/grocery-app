package com.grocery.payment.service.impl;

import com.grocery.events.inventory.ReservedItem;
import com.grocery.events.inventory.StockReserved;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderLine;
import com.grocery.events.payment.PaymentConfirmed;
import com.grocery.events.payment.RefundCompleted;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import com.grocery.payment.exception.ResourceNotFoundException;
import com.grocery.payment.gateway.MockPaymentGateway;
import com.grocery.payment.mapper.PaymentEventMapper;
import com.grocery.payment.messaging.OutboxEventPublisher;
import com.grocery.payment.repository.PaymentRepository;
import com.grocery.payment.service.ProcessedEventService;
import com.grocery.payment.support.TestPaymentProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentSagaServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;
    @Mock
    private ProcessedEventService processedEventService;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private PaymentSagaServiceImpl sagaService;

    @BeforeEach
    void setUp() {
        sagaService = new PaymentSagaServiceImpl(paymentRepository, processedEventService, new MockPaymentGateway(),
                new PaymentEventMapper(), outboxEventPublisher, TestPaymentProperties.razorpay(), TestPaymentProperties.FIXED_CLOCK);
    }

    @Test
    void orderCreated_createsAPendingPaymentForTheOrderTotal() {
        when(processedEventService.markAsProcessed(anyString(), eq("OrderCreated"))).thenReturn(true);
        when(paymentRepository.existsByOrderId(10L)).thenReturn(false);

        sagaService.handleOrderCreated(orderCreated(10L, "ONLINE"));

        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("158.00");
        assertThat(saved.getValue().getMethod()).isEqualTo(PaymentMethod.ONLINE);
    }

    @Test
    void stockReserved_cod_confirmsThePaymentRightAway() {
        Payment payment = payment(10L, PaymentMethod.COD);
        when(processedEventService.markAsProcessed(anyString(), eq("StockReserved"))).thenReturn(true);
        when(paymentRepository.findByOrderId(10L)).thenReturn(Optional.of(payment));

        sagaService.handleStockReserved(stockReserved(10L));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CASH_ON_DELIVERY);
        verify(outboxEventPublisher).publish(eq("payment"), eq(10L), any(PaymentConfirmed.class));
    }

    @Test
    void stockReserved_online_waitsForTheCustomerToPay() {
        Payment payment = payment(11L, PaymentMethod.ONLINE);
        when(processedEventService.markAsProcessed(anyString(), eq("StockReserved"))).thenReturn(true);
        when(paymentRepository.findByOrderId(11L)).thenReturn(Optional.of(payment));

        sagaService.handleStockReserved(stockReserved(11L));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.AWAITING_PAYMENT);
        assertThat(payment.getGatewayOrderId()).isEqualTo("mock_order_11");
        assertThat(payment.getPayBefore()).isEqualTo(Instant.parse("2026-10-26T10:15:00Z"));   // now + 15 minutes
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void stockReserved_beforeOrderCreated_failsSoThatKafkaRetriesIt() {
        when(processedEventService.markAsProcessed(anyString(), eq("StockReserved"))).thenReturn(true);
        when(paymentRepository.findByOrderId(12L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sagaService.handleStockReserved(stockReserved(12L)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("will retry");
    }

    @Test
    void orderCancelled_afterPaying_refundsThePayment() {
        Payment payment = payment(13L, PaymentMethod.ONLINE);
        payment.awaitOnlinePayment("mock_order_13", Instant.now());
        payment.markPaid("mock_pay_13", Instant.now());
        when(processedEventService.markAsProcessed("e-13", "OrderCancelled")).thenReturn(true);
        when(paymentRepository.findByOrderId(13L)).thenReturn(Optional.of(payment));

        sagaService.handleOrderCancelled("e-13", "OrderCancelled", 13L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundReference()).startsWith("mock_refund_");
        verify(outboxEventPublisher).publish(eq("payment"), eq(13L), any(RefundCompleted.class));
    }

    @Test
    void orderRejected_beforePaying_justCancelsThePayment() {
        Payment payment = payment(14L, PaymentMethod.COD);
        payment.acceptCashOnDelivery();
        when(processedEventService.markAsProcessed("e-14", "OrderRejected")).thenReturn(true);
        when(paymentRepository.findByOrderId(14L)).thenReturn(Optional.of(payment));

        sagaService.handleOrderCancelled("e-14", "OrderRejected", 14L);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        verifyNoInteractions(outboxEventPublisher);
    }

    @Test
    void duplicateEvent_isSkipped() {
        when(processedEventService.markAsProcessed("e-15", "OrderCancelled")).thenReturn(false);

        sagaService.handleOrderCancelled("e-15", "OrderCancelled", 15L);

        verifyNoInteractions(paymentRepository, outboxEventPublisher);
    }

    private Payment payment(Long orderId, PaymentMethod method) {
        Payment payment = Payment.forOrder(orderId, 4L, 1L, new BigDecimal("158.00"), method);
        payment.setId(orderId + 100);
        return payment;
    }

    private OrderCreated orderCreated(Long orderId, String paymentMethod) {
        return OrderCreated.newBuilder()
                .setEventId("oc-" + orderId).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerUserId(4L).setStoreId(1L)
                .setItems(List.of(new OrderLine(1L, "Tomato", 2, "36.00")))
                .setItemsTotal("128.00").setCouponCode(null).setCouponDiscount("0.00").setDeliveryFee("30.00")
                .setTotalAmount("158.00").setPaymentMethod(paymentMethod)
                .build();
    }

    private StockReserved stockReserved(Long orderId) {
        return StockReserved.newBuilder()
                .setEventId("sr-" + orderId).setOccurredAt(Instant.now())
                .setOrderId(orderId).setStoreId(1L)
                .setItems(List.of(new ReservedItem(1L, 2)))
                .build();
    }
}
