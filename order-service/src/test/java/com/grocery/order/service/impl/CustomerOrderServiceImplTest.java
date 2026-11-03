package com.grocery.order.service.impl;

import com.grocery.events.order.OrderCreated;
import com.grocery.order.client.CartClient;
import com.grocery.order.client.CustomerClient;
import com.grocery.order.client.StoreClient;
import com.grocery.order.config.OrderProperties;
import com.grocery.order.dto.request.PlaceOrderRequest;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.exception.BusinessException;
import com.grocery.order.exception.CheckoutChangedException;
import com.grocery.order.exception.CheckoutNotReadyException;
import com.grocery.order.exception.DuplicateResourceException;
import com.grocery.order.mapper.OrderEventMapper;
import com.grocery.order.mapper.OrderMapper;
import com.grocery.order.messaging.OutboxEventPublisher;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.service.InvoiceService;
import com.grocery.order.service.OrderCanceller;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static com.grocery.order.support.TestOrders.freshMart;
import static com.grocery.order.support.TestOrders.homeAddress;
import static com.grocery.order.support.TestOrders.notReadyCheckout;
import static com.grocery.order.support.TestOrders.pendingOrder;
import static com.grocery.order.support.TestOrders.readyCheckout;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerOrderServiceImplTest {

    private static final Long CUSTOMER = 4L;
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private CartClient cartClient;
    @Mock
    private CustomerClient customerClient;
    @Mock
    private StoreClient storeClient;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;
    @Mock
    private OrderCanceller orderCanceller;

    private CustomerOrderServiceImpl service(boolean requireStoreOpen) {
        // A TransactionTemplate around a mock transaction manager: runs the callback, no real database
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        return new CustomerOrderServiceImpl(orderRepository, cartClient, customerClient, storeClient, new OrderMapper(),
                new OrderEventMapper(), outboxEventPublisher, orderCanceller, new OrderProperties(requireStoreOpen),
                mock(InvoiceService.class), transactionTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void placeOrder_savesAPendingOrder_andPublishesOrderCreatedWithTheCartTotals() {
        when(cartClient.getCheckoutSummary(CUSTOMER)).thenReturn(readyCheckout());
        when(customerClient.findAddress(CUSTOMER, 1L)).thenReturn(Optional.of(homeAddress("560038")));
        when(storeClient.findStore(1L)).thenReturn(Optional.of(freshMart(true)));
        saveGivesId(100L);

        OrderResponse order = service(true).placeOrder(CUSTOMER, new PlaceOrderRequest(1L, PaymentMethod.COD, " Ring twice "), null);

        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.amounts().totalAmount()).isEqualByComparingTo("158.00");
        assertThat(order.customerNote()).isEqualTo("Ring twice");
        assertThat(order.storeAddress()).isEqualTo("45, 100 Feet Road, Indiranagar, Bengaluru 560038");
        assertThat(order.paymentUrl()).isNull();   // cash on delivery: nothing to pay online

        ArgumentCaptor<OrderCreated> event = ArgumentCaptor.forClass(OrderCreated.class);
        verify(outboxEventPublisher).publish(eq("order"), eq(100L), event.capture());
        assertThat(event.getValue().getItems()).hasSize(2);
        assertThat(event.getValue().getTotalAmount()).isEqualTo("158.00");
        assertThat(event.getValue().getPaymentMethod()).isEqualTo("COD");
    }

    @Test
    void placeOrder_online_answersWithThePageWhereTheCustomerPays() {
        when(cartClient.getCheckoutSummary(CUSTOMER)).thenReturn(readyCheckout());
        when(customerClient.findAddress(CUSTOMER, 1L)).thenReturn(Optional.of(homeAddress("560038")));
        when(storeClient.findStore(1L)).thenReturn(Optional.of(freshMart(true)));
        saveGivesId(100L);

        OrderResponse order = service(true).placeOrder(CUSTOMER, new PlaceOrderRequest(1L, PaymentMethod.ONLINE, null), null);

        assertThat(order.paymentMethod()).isEqualTo(PaymentMethod.ONLINE);
        assertThat(order.paymentStatus()).isEqualTo(OrderPaymentStatus.PENDING);
        assertThat(order.paymentUrl()).isEqualTo("/orders/100");
    }

    @Test
    void placeOrder_withProblemsInTheCart_isRejectedWithEveryProblem() {
        when(cartClient.getCheckoutSummary(CUSTOMER)).thenReturn(notReadyCheckout("Tomato is out of stock", "The minimum order value is 99.00"));

        assertThatThrownBy(() -> service(true).placeOrder(CUSTOMER, new PlaceOrderRequest(1L, PaymentMethod.COD, null), null))
                .isInstanceOf(CheckoutNotReadyException.class)
                .satisfies(ex -> assertThat(((CheckoutNotReadyException) ex).getProblems()).hasSize(2));
        verifyNoInteractions(orderRepository, outboxEventPublisher);
    }

    @Test
    void placeOrder_toAnAddressOutsideTheDeliveryArea_isRejected() {
        when(cartClient.getCheckoutSummary(CUSTOMER)).thenReturn(readyCheckout());
        when(customerClient.findAddress(CUSTOMER, 2L)).thenReturn(Optional.of(homeAddress("560034")));
        when(storeClient.findStore(1L)).thenReturn(Optional.of(freshMart(true)));

        assertThatThrownBy(() -> service(true).placeOrder(CUSTOMER, new PlaceOrderRequest(2L, PaymentMethod.COD, null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Fresh Mart does not deliver to PIN code 560034. Please choose another address.");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_whileTheStoreIsClosed_dependsOnTheSetting() {
        when(cartClient.getCheckoutSummary(CUSTOMER)).thenReturn(readyCheckout());
        when(customerClient.findAddress(CUSTOMER, 1L)).thenReturn(Optional.of(homeAddress("560038")));
        when(storeClient.findStore(1L)).thenReturn(Optional.of(freshMart(false)));

        assertThatThrownBy(() -> service(true).placeOrder(CUSTOMER, new PlaceOrderRequest(1L, PaymentMethod.COD, null), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("Fresh Mart is closed right now");

        saveGivesId(101L);
        assertThat(service(false).placeOrder(CUSTOMER, new PlaceOrderRequest(1L, PaymentMethod.COD, null), null).status())
                .isEqualTo(OrderStatus.PENDING);
    }

    @Test
    void placeOrder_remembersTheKeyAndTheFingerprintOfTheCart() {
        when(cartClient.getCheckoutSummary(CUSTOMER)).thenReturn(readyCheckout());
        when(customerClient.findAddress(CUSTOMER, 1L)).thenReturn(Optional.of(homeAddress("560038")));
        when(storeClient.findStore(1L)).thenReturn(Optional.of(freshMart(true)));
        saveGivesId(100L);

        service(true).placeOrder(CUSTOMER, new PlaceOrderRequest(1L, PaymentMethod.COD, null, "fp-ready-cart-001"), "chat-card-0001");

        ArgumentCaptor<Order> saved = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(saved.capture());
        assertThat(saved.getValue().getIdempotencyKey()).isEqualTo("chat-card-0001");
        assertThat(saved.getValue().getCheckoutFingerprint()).isEqualTo("fp-ready-cart-001");
    }

    @Test
    void placeOrder_repeatedWithTheSameKey_givesTheFirstOrder_withoutReadingTheCart() {
        Order placed = pendingOrder(PaymentMethod.COD);
        placed.setId(100L);
        when(orderRepository.findByCustomerUserIdAndIdempotencyKey(CUSTOMER, "chat-card-0001")).thenReturn(Optional.of(placed));

        OrderResponse order = service(true).placeOrder(CUSTOMER, new PlaceOrderRequest(1L, PaymentMethod.COD, null), "chat-card-0001");

        assertThat(order.id()).isEqualTo(100L);
        // after a timeout the order exists while its cart may be empty already: the cart is not even read
        verifyNoInteractions(cartClient, outboxEventPublisher);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_fromASummaryOfAnotherCart_isRefused() {
        when(cartClient.getCheckoutSummary(CUSTOMER)).thenReturn(readyCheckout());

        assertThatThrownBy(() -> service(true).placeOrder(CUSTOMER,
                new PlaceOrderRequest(1L, PaymentMethod.COD, null, "fp-of-an-older-cart"), "chat-card-0002"))
                .isInstanceOf(CheckoutChangedException.class)
                .hasMessage("Your cart or its prices changed since the summary. Please check the new total.");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_ofTheSameCartAMomentAgo_isRefusedAsADoubleOrder() {
        when(cartClient.getCheckoutSummary(CUSTOMER)).thenReturn(readyCheckout());
        Order earlier = pendingOrder(PaymentMethod.COD);
        earlier.setId(99L);
        when(orderRepository.findRecentOrdersOfCart(CUSTOMER, "fp-ready-cart-001", NOW.minus(Duration.ofMinutes(2))))
                .thenReturn(List.of(earlier));

        assertThatThrownBy(() -> service(true).placeOrder(CUSTOMER, new PlaceOrderRequest(1L, PaymentMethod.COD, null), "chat-card-0003"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("You placed this order a moment ago (order #99).");
        verify(orderRepository, never()).save(any());
    }

    @Test
    void placeOrder_twoRequestsWithTheSameKeyAtOnce_bothGetTheOneOrder() {
        when(cartClient.getCheckoutSummary(CUSTOMER)).thenReturn(readyCheckout());
        when(customerClient.findAddress(CUSTOMER, 1L)).thenReturn(Optional.of(homeAddress("560038")));
        when(storeClient.findStore(1L)).thenReturn(Optional.of(freshMart(true)));
        // the other request saved first: the unique key (customer, key) refuses this insert
        when(orderRepository.save(any(Order.class))).thenThrow(new DataIntegrityViolationException("Duplicate entry 'chat-card-0004'"));
        Order other = pendingOrder(PaymentMethod.COD);
        other.setId(101L);
        when(orderRepository.findByCustomerUserIdAndIdempotencyKey(CUSTOMER, "chat-card-0004"))
                .thenReturn(Optional.empty(), Optional.of(other));

        OrderResponse order = service(true).placeOrder(CUSTOMER, new PlaceOrderRequest(1L, PaymentMethod.COD, null), "chat-card-0004");

        assertThat(order.id()).isEqualTo(101L);
    }

    /** What the database does on insert: the new row gets an id. */
    private void saveGivesId(Long id) {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(id);
            return order;
        });
    }
}
