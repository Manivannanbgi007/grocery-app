package com.grocery.cart.entity;

import com.grocery.cart.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * One product in a cart, with a copy ("snapshot") of the product's details.
 * Prices include tax (like the price printed on the pack in India).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cart_items")
public class CartItem extends BaseEntity {

    public static final int MAX_QUANTITY = 20;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "unit_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal unitQuantity;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /** Price before the product discount (MRP). */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    /** Price the customer pays for one unit. */
    @Column(name = "selling_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal sellingPrice;

    @Column(name = "tax_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Column(nullable = false)
    private int quantity;

    public void changeQuantity(int newQuantity) {
        if (newQuantity < 1 || newQuantity > MAX_QUANTITY) {
            throw new BusinessException("Quantity must be between 1 and " + MAX_QUANTITY);
        }
        this.quantity = newQuantity;
    }

    /** Takes the name and prices of another snapshot of the same product (newer catalog data). */
    public void copyProductDetailsFrom(CartItem latest) {
        this.productName = latest.productName;
        this.unit = latest.unit;
        this.unitQuantity = latest.unitQuantity;
        this.imageUrl = latest.imageUrl;
        this.price = latest.price;
        this.sellingPrice = latest.sellingPrice;
        this.taxPercentage = latest.taxPercentage;
    }

    public BigDecimal lineTotal() {
        return sellingPrice.multiply(BigDecimal.valueOf(quantity));
    }

    /** Price before discount x quantity. */
    public BigDecimal lineTotalBeforeDiscount() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }
}
