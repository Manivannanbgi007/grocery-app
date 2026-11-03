package com.grocery.order.mapper;

import com.grocery.events.order.OrderReadyForPickup;
import com.grocery.order.entity.DeliveryAddress;
import com.grocery.order.entity.Order;
import com.grocery.order.entity.OrderAmounts;
import com.grocery.order.entity.StoreDetails;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.support.TestOrders;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** OrderReadyForPickup carries what delivery-service needs, including the coordinates for the tracking map. */
class OrderEventMapperTest {

    private final OrderEventMapper mapper = new OrderEventMapper();

    @Test
    void readyForPickup_carriesTheCoordinatesOfTheStoreAndTheAddress() {
        Order order = TestOrders.pendingOrder(PaymentMethod.COD);
        order.setId(18L);

        OrderReadyForPickup event = mapper.toOrderReadyForPickup(order);

        assertThat(event.getStoreLatitude()).isEqualTo(12.9784);
        assertThat(event.getStoreLongitude()).isEqualTo(77.6408);
        assertThat(event.getDeliveryLatitude()).isEqualTo(12.9719);
        assertThat(event.getDeliveryLongitude()).isEqualTo(77.6412);
    }

    @Test
    void readyForPickup_withoutCoordinates_sendsNull() {
        Order order = Order.place(4L, 1L,
                new StoreDetails("Fresh Mart", "9000000003", "45, 100 Feet Road, Bengaluru 560038", "560038",
                        new BigDecimal("10.00"), null, null),
                new DeliveryAddress("Priya Sharma", "9000000004", "12, 5th Cross", null, null, "Bengaluru", "Karnataka",
                        "560038", null, null),
                PaymentMethod.ONLINE,
                new OrderAmounts(new BigDecimal("40.00"), null, BigDecimal.ZERO, new BigDecimal("30.00"),
                        new BigDecimal("2.00"), new BigDecimal("70.00")),
                null, List.of());
        order.setId(19L);

        OrderReadyForPickup event = mapper.toOrderReadyForPickup(order);

        assertThat(event.getStoreLatitude()).isNull();
        assertThat(event.getDeliveryLongitude()).isNull();
        assertThat(event.getAmountToCollect()).isEqualTo("0.00");   // paid online: nothing to collect
    }
}
