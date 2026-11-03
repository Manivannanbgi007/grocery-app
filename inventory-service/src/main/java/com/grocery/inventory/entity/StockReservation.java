package com.grocery.inventory.entity;

import com.grocery.inventory.enums.ReservationStatus;
import com.grocery.inventory.exception.BusinessException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The units held for one order (one reservation per order). It remembers what was reserved,
 * so a cancellation can give back exactly those units.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use StockReservation.reserve(...)
@Entity
@Table(name = "stock_reservations")
public class StockReservation extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private Long orderId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @OneToMany(mappedBy = "reservation", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productId")
    private List<StockReservationItem> items = new ArrayList<>();

    /** @param quantities productId -> units */
    public static StockReservation reserve(Long orderId, Long storeId, Map<Long, Integer> quantities) {
        StockReservation reservation = new StockReservation();
        reservation.orderId = orderId;
        reservation.storeId = storeId;
        reservation.status = ReservationStatus.RESERVED;
        quantities.forEach((productId, quantity) ->
                reservation.items.add(new StockReservationItem(reservation, productId, quantity)));
        return reservation;
    }

    public boolean isReserved() {
        return status == ReservationStatus.RESERVED;
    }

    public void markReleased() {
        requireReserved();
        this.status = ReservationStatus.RELEASED;
    }

    public void markCommitted() {
        requireReserved();
        this.status = ReservationStatus.COMMITTED;
    }

    private void requireReserved() {
        if (!isReserved()) {
            throw new BusinessException("The reservation of order %d is already %s".formatted(orderId, status));
        }
    }
}
