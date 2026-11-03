package com.grocery.order.live;

import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.dto.response.OrderChange;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.support.TestOrders;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** An order event from Kafka becomes a live update for the order's store, and never an error. */
@ExtendWith(MockitoExtension.class)
class OrderChangeListenerTest {

    @Mock
    private VendorOrderStreams streams;
    @Mock
    private OrderRepository orderRepository;
    @InjectMocks
    private OrderChangeListener listener;

    @Test
    void anOrderEvent_tellsTheStreamsOfThatOrdersStore() {
        Order order = TestOrders.pendingOrder(PaymentMethod.COD);   // Fresh Mart, store 1
        order.setId(194L);
        when(streams.hasOpenStreams()).thenReturn(true);
        when(orderRepository.findById(194L)).thenReturn(Optional.of(order));

        listener.onOrderEvent(event("194"));

        verify(streams).sendOrderChange(1L, new OrderChange(194L, OrderStatus.PENDING));
    }

    @Test
    void withoutOpenStreams_theOrderIsNotEvenRead() {
        when(streams.hasOpenStreams()).thenReturn(false);

        listener.onOrderEvent(event("194"));

        verifyNoInteractions(orderRepository);
        verify(streams, never()).sendOrderChange(any(), any());
    }

    @Test
    void aProblem_isLoggedButNeverThrown() {
        // A thrown exception would make Kafka retry the event and finally copy it to the dead-letter topic
        when(streams.hasOpenStreams()).thenReturn(true);
        when(orderRepository.findById(194L)).thenThrow(new DataAccessResourceFailureException("MySQL is restarting"));

        assertThatCode(() -> listener.onOrderEvent(event("194"))).doesNotThrowAnyException();
        assertThatCode(() -> listener.onOrderEvent(event("not-an-order-id"))).doesNotThrowAnyException();
        assertThatCode(() -> listener.onOrderEvent(event(null))).doesNotThrowAnyException();
        verify(streams, never()).sendOrderChange(any(), any());
    }

    /** An event as Debezium sends it: the key is the order id as text (the Avro value is not read). */
    private static ConsumerRecord<String, byte[]> event(String key) {
        return new ConsumerRecord<>(KafkaTopics.ORDER_EVENTS, 0, 0L, key, new byte[0]);
    }
}
