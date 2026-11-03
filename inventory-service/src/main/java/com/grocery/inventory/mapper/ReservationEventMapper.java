package com.grocery.inventory.mapper;

import com.grocery.events.inventory.ReservedItem;
import com.grocery.events.inventory.StockReleased;
import com.grocery.events.inventory.StockReservationFailed;
import com.grocery.events.inventory.StockReserved;
import com.grocery.events.inventory.UnavailableItem;
import com.grocery.inventory.entity.StockReservation;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Builds the reservation events of the order saga. Every event gets a new unique eventId.
 */
@Component
public class ReservationEventMapper {

    public StockReserved toStockReserved(StockReservation reservation) {
        return StockReserved.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setOrderId(reservation.getOrderId())
                .setStoreId(reservation.getStoreId())
                .setItems(reservation.getItems().stream()
                        .map(item -> new ReservedItem(item.getProductId(), item.getQuantity()))
                        .toList())
                .build();
    }

    public StockReservationFailed toStockReservationFailed(Long orderId, Long storeId, List<UnavailableItem> unavailableItems) {
        return StockReservationFailed.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setOrderId(orderId)
                .setStoreId(storeId)
                .setReason("Not enough stock for %d product(s)".formatted(unavailableItems.size()))
                .setItems(unavailableItems)
                .build();
    }

    public StockReleased toStockReleased(StockReservation reservation) {
        return StockReleased.newBuilder()
                .setEventId(newEventId())
                .setOccurredAt(Instant.now())
                .setOrderId(reservation.getOrderId())
                .setStoreId(reservation.getStoreId())
                .build();
    }

    private String newEventId() {
        return UUID.randomUUID().toString();
    }
}
