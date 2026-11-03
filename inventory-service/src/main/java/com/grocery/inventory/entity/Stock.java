package com.grocery.inventory.entity;

import com.grocery.inventory.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Stock of one product in its store (DDD aggregate root).
 *
 *   quantityOnHand    units physically in the store
 *   reservedQuantity  units promised to orders that are not delivered yet (Phase 7)
 *   available         quantityOnHand - reservedQuantity = what customers can still buy
 *
 * All changes go through the methods below, which protect the rules:
 * quantities are never negative and never more than MAX_QUANTITY, and the store
 * never has fewer units than it has promised (quantityOnHand >= reservedQuantity).
 * That is why there are no setters for the quantities.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use Stock.create(...)
@Entity
@Table(name = "stock_items")
public class Stock extends BaseEntity {

    /** Upper limit for any quantity; stops typing mistakes such as 1000000000. */
    public static final int MAX_QUANTITY = 1_000_000;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Column(name = "low_stock_threshold", nullable = false)
    private int lowStockThreshold;

    public static Stock create(Long productId, Long storeId, int quantityOnHand, int lowStockThreshold) {
        Stock stock = new Stock();
        stock.productId = productId;
        stock.storeId = storeId;
        stock.reservedQuantity = 0;
        stock.setQuantityOnHand(quantityOnHand);
        stock.setLowStockThreshold(lowStockThreshold);
        return stock;
    }

    public int getAvailableQuantity() {
        return quantityOnHand - reservedQuantity;
    }

    /** Low stock = available units at or below the threshold (an empty shelf is low stock too). */
    public boolean isLowStock() {
        return getAvailableQuantity() <= lowStockThreshold;
    }

    public boolean isOutOfStock() {
        return getAvailableQuantity() == 0;
    }

    // ----- changes made by the vendor -----

    /** The vendor counted the shelf: set the exact number of units in the store. */
    public void setQuantityOnHand(int newQuantityOnHand) {
        checkQuantityRange(newQuantityOnHand, "Quantity on hand");
        if (newQuantityOnHand < reservedQuantity) {
            throw new BusinessException("Quantity on hand cannot be less than the %d units reserved for open orders"
                    .formatted(reservedQuantity));
        }
        this.quantityOnHand = newQuantityOnHand;
    }

    /** New goods arrived: add them to the units in the store. */
    public void addStock(int quantity) {
        checkPositive(quantity);
        if (quantityOnHand + quantity > MAX_QUANTITY) {
            throw new BusinessException("Quantity on hand cannot be more than " + MAX_QUANTITY);
        }
        this.quantityOnHand += quantity;
    }

    public void setLowStockThreshold(int newThreshold) {
        checkQuantityRange(newThreshold, "Low-stock threshold");
        this.lowStockThreshold = newThreshold;
    }

    // ----- changes made by the order saga (wired in Phase 7) -----

    /** An order was placed: hold units for it so nobody else can buy them. */
    public void reserve(int quantity) {
        checkPositive(quantity);
        if (quantity > getAvailableQuantity()) {
            throw new BusinessException("Only %d unit(s) of product %d available, %d requested"
                    .formatted(getAvailableQuantity(), productId, quantity));
        }
        this.reservedQuantity += quantity;
    }

    /** The order was cancelled or rejected: give the held units back. */
    public void release(int quantity) {
        checkPositive(quantity);
        if (quantity > reservedQuantity) {
            throw new BusinessException("Cannot release %d unit(s) of product %d: only %d reserved"
                    .formatted(quantity, productId, reservedQuantity));
        }
        this.reservedQuantity -= quantity;
    }

    /** The order left the store: the held units are gone from the shelf for good. */
    public void commitReservation(int quantity) {
        release(quantity);
        this.quantityOnHand -= quantity;
    }

    private void checkPositive(int quantity) {
        if (quantity <= 0) {
            throw new BusinessException("Quantity must be at least 1");
        }
    }

    private void checkQuantityRange(int value, String name) {
        if (value < 0 || value > MAX_QUANTITY) {
            throw new BusinessException("%s must be between 0 and %d".formatted(name, MAX_QUANTITY));
        }
    }
}
