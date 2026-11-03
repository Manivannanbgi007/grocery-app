package com.grocery.inventory.entity;

import com.grocery.inventory.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The stock rules (plain JUnit - no Spring, no database).
 */
class StockTest {

    @Test
    void availableQuantity_isOnHandMinusReserved() {
        Stock stock = Stock.create(1L, 1L, 10, 2);

        stock.reserve(4);

        assertThat(stock.getQuantityOnHand()).isEqualTo(10);
        assertThat(stock.getReservedQuantity()).isEqualTo(4);
        assertThat(stock.getAvailableQuantity()).isEqualTo(6);
    }

    @Test
    void lowStock_whenAvailableUnitsReachTheThreshold() {
        Stock stock = Stock.create(1L, 1L, 6, 5);
        assertThat(stock.isLowStock()).isFalse();

        stock.reserve(1);

        assertThat(stock.isLowStock()).isTrue();
        assertThat(stock.isOutOfStock()).isFalse();
    }

    @Test
    void outOfStock_whenNothingIsAvailable() {
        Stock stock = Stock.create(1L, 1L, 3, 0);

        stock.reserve(3);

        assertThat(stock.isOutOfStock()).isTrue();
        assertThat(stock.isLowStock()).isTrue();
    }

    @Test
    void create_rejectsANegativeQuantity() {
        assertThatThrownBy(() -> Stock.create(1L, 1L, -1, 5))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quantity on hand must be between 0 and 1000000");
    }

    @Test
    void setQuantityOnHand_cannotGoBelowTheReservedUnits() {
        Stock stock = Stock.create(1L, 1L, 10, 2);
        stock.reserve(4);

        assertThatThrownBy(() -> stock.setQuantityOnHand(3))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quantity on hand cannot be less than the 4 units reserved for open orders");
        assertThat(stock.getQuantityOnHand()).isEqualTo(10);
    }

    @Test
    void addStock_addsToTheUnitsOnHand() {
        Stock stock = Stock.create(1L, 1L, 10, 2);

        stock.addStock(15);

        assertThat(stock.getQuantityOnHand()).isEqualTo(25);
    }

    @Test
    void addStock_rejectsZeroAndTooMuch() {
        Stock stock = Stock.create(1L, 1L, Stock.MAX_QUANTITY - 1, 2);

        assertThatThrownBy(() -> stock.addStock(0)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> stock.addStock(2))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quantity on hand cannot be more than 1000000");
    }

    @Test
    void reserve_failsWhenNotEnoughIsAvailable_andChangesNothing() {
        Stock stock = Stock.create(7L, 1L, 5, 2);
        stock.reserve(3);

        assertThatThrownBy(() -> stock.reserve(3))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only 2 unit(s) of product 7 available, 3 requested");
        assertThat(stock.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    void release_givesReservedUnitsBack() {
        Stock stock = Stock.create(1L, 1L, 10, 2);
        stock.reserve(4);

        stock.release(4);

        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(stock.getAvailableQuantity()).isEqualTo(10);
    }

    @Test
    void release_cannotGiveBackMoreThanWasReserved() {
        Stock stock = Stock.create(1L, 1L, 10, 2);
        stock.reserve(1);

        assertThatThrownBy(() -> stock.release(2)).isInstanceOf(BusinessException.class);
    }

    @Test
    void commitReservation_removesTheUnitsFromTheShelf() {
        Stock stock = Stock.create(1L, 1L, 10, 2);
        stock.reserve(4);

        stock.commitReservation(4);

        assertThat(stock.getQuantityOnHand()).isEqualTo(6);
        assertThat(stock.getReservedQuantity()).isZero();
        assertThat(stock.getAvailableQuantity()).isEqualTo(6);
    }
}
