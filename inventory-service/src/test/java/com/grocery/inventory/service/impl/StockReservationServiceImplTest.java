package com.grocery.inventory.service.impl;

import com.grocery.events.inventory.StockReleased;
import com.grocery.events.inventory.StockReservationFailed;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderLine;
import com.grocery.inventory.entity.Stock;
import com.grocery.inventory.entity.StockReservation;
import com.grocery.inventory.enums.ReservationStatus;
import com.grocery.inventory.mapper.ReservationEventMapper;
import com.grocery.inventory.mapper.StockEventMapper;
import com.grocery.inventory.messaging.OutboxEventPublisher;
import com.grocery.inventory.messaging.StockEventPublisher;
import com.grocery.inventory.repository.StockRepository;
import com.grocery.inventory.repository.StockReservationRepository;
import com.grocery.inventory.service.ProcessedEventService;
import org.apache.avro.specific.SpecificRecordBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReservationServiceImplTest {

    @Mock
    private StockRepository stockRepository;
    @Mock
    private StockReservationRepository reservationRepository;
    @Mock
    private ProcessedEventService processedEventService;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private StockReservationServiceImpl reservationService;

    @BeforeEach
    void setUp() {
        StockEventPublisher stockEventPublisher = new StockEventPublisher(stockRepository, new StockEventMapper(), outboxEventPublisher);
        reservationService = new StockReservationServiceImpl(stockRepository, reservationRepository, processedEventService,
                stockEventPublisher, new ReservationEventMapper(), outboxEventPublisher);
    }

    @Test
    void enoughStock_reservesEveryItem_andPublishesStockReserved() {
        Stock tomato = stock(1L, 120);
        Stock butter = stock(7L, 3);
        when(processedEventService.markAsProcessed(anyString(), eq("OrderCreated"))).thenReturn(true);
        when(stockRepository.findByStoreIdAndProductIdIn(eq(1L), any())).thenReturn(List.of(tomato, butter));
        when(reservationRepository.save(any(StockReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        reservationService.reserveForOrder(orderCreated(10L, Map.of(1L, 2, 7L, 3)));

        assertThat(tomato.getReservedQuantity()).isEqualTo(2);
        assertThat(butter.getAvailableQuantity()).isZero();
        assertThat(publishedEventTypes()).contains("StockReserved", "StockChanged").doesNotContain("StockReservationFailed");
    }

    @Test
    void oneItemShort_reservesNothing_andSaysWhichItemIsShort() {
        Stock tomato = stock(1L, 120);
        Stock butter = stock(7L, 3);
        when(processedEventService.markAsProcessed(anyString(), eq("OrderCreated"))).thenReturn(true);
        when(stockRepository.findByStoreIdAndProductIdIn(eq(1L), any())).thenReturn(List.of(tomato, butter));

        reservationService.reserveForOrder(orderCreated(11L, Map.of(1L, 2, 7L, 5)));

        assertThat(tomato.getReservedQuantity()).isZero();   // all or nothing
        assertThat(butter.getReservedQuantity()).isZero();
        ArgumentCaptor<SpecificRecordBase> event = ArgumentCaptor.forClass(SpecificRecordBase.class);
        verify(outboxEventPublisher).publish(eq("inventory"), eq(11L), event.capture());
        StockReservationFailed failed = (StockReservationFailed) event.getValue();
        assertThat(failed.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getProductId()).isEqualTo(7L);
            assertThat(item.getRequestedQuantity()).isEqualTo(5);
            assertThat(item.getAvailableQuantity()).isEqualTo(3);
        });
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void productWithoutAStockRecord_countsAsNotAvailable() {
        when(processedEventService.markAsProcessed(anyString(), eq("OrderCreated"))).thenReturn(true);
        when(stockRepository.findByStoreIdAndProductIdIn(eq(1L), any())).thenReturn(List.of());

        reservationService.reserveForOrder(orderCreated(12L, Map.of(99L, 1)));

        verify(outboxEventPublisher).publish(eq("inventory"), eq(12L), any(StockReservationFailed.class));
    }

    @Test
    void cancellation_givesTheHeldUnitsBack() {
        Stock tomato = stock(1L, 120);
        tomato.reserve(2);
        StockReservation reservation = StockReservation.reserve(13L, 1L, Map.of(1L, 2));
        when(processedEventService.markAsProcessed("e-13", "OrderCancelled")).thenReturn(true);
        when(reservationRepository.findByOrderId(13L)).thenReturn(Optional.of(reservation));
        when(stockRepository.findByProductIdAndStoreId(1L, 1L)).thenReturn(Optional.of(tomato));

        reservationService.releaseForOrder("e-13", "OrderCancelled", 13L);

        assertThat(tomato.getReservedQuantity()).isZero();
        assertThat(reservation.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(outboxEventPublisher).publish(eq("inventory"), eq(13L), any(StockReleased.class));
    }

    @Test
    void cancellationOfAnOrderThatWasNeverReserved_doesNothing() {
        when(processedEventService.markAsProcessed("e-14", "OrderCancelled")).thenReturn(true);
        when(reservationRepository.findByOrderId(14L)).thenReturn(Optional.empty());

        reservationService.releaseForOrder("e-14", "OrderCancelled", 14L);

        verify(outboxEventPublisher, never()).publish(any(), any(), any());
    }

    private List<String> publishedEventTypes() {
        ArgumentCaptor<SpecificRecordBase> events = ArgumentCaptor.forClass(SpecificRecordBase.class);
        verify(outboxEventPublisher, atLeastOnce()).publish(eq("inventory"), any(), events.capture());
        return events.getAllValues().stream().map(event -> event.getSchema().getName()).toList();
    }

    private Stock stock(Long productId, int quantityOnHand) {
        Stock stock = Stock.create(productId, 1L, quantityOnHand, 5);
        stock.setVersion(1L);
        return stock;
    }

    private OrderCreated orderCreated(Long orderId, Map<Long, Integer> quantities) {
        return OrderCreated.newBuilder()
                .setEventId("oc-" + orderId).setOccurredAt(Instant.now())
                .setOrderId(orderId).setCustomerUserId(4L).setStoreId(1L)
                .setItems(quantities.entrySet().stream()
                        .map(entry -> new OrderLine(entry.getKey(), "Product " + entry.getKey(), entry.getValue(), "10.00"))
                        .toList())
                .setItemsTotal("100.00").setCouponCode(null).setCouponDiscount("0.00").setDeliveryFee("30.00")
                .setTotalAmount("130.00").setPaymentMethod("COD")
                .build();
    }
}
