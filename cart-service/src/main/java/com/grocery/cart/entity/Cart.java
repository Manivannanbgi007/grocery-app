package com.grocery.cart.entity;

import com.grocery.cart.exception.BusinessException;
import com.grocery.cart.exception.CartStoreConflictException;
import com.grocery.cart.exception.ResourceNotFoundException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * A customer's shopping cart (DDD aggregate root). One cart per customer.
 *
 * Main rule: ONE STORE PER CART. All items come from the same store, because one order
 * goes to one store. Adding a product of another store fails with 409 (CartStoreConflictException).
 *
 * Items keep a copy of the product's name and prices from the moment they were added.
 * The checkout refreshes them with the current catalog values.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use Cart.newCart(...)
@Entity
@Table(name = "carts")
public class Cart extends BaseEntity {

    public static final int MAX_DIFFERENT_ITEMS = 50;

    @Column(name = "customer_user_id", nullable = false, updatable = false)
    private Long customerUserId;

    /** Store of the items in the cart; null while the cart is empty. */
    @Column(name = "store_id")
    private Long storeId;

    @Column(name = "coupon_code", length = 20)
    private String couponCode;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<CartItem> items = new ArrayList<>();

    public static Cart newCart(Long customerUserId) {
        Cart cart = new Cart();
        cart.customerUserId = customerUserId;
        return cart;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    /** true when the cart already holds items of a different store than the given one. */
    public boolean hasItemsFromAnotherStore(Long otherStoreId) {
        return !isEmpty() && !storeId.equals(otherStoreId);
    }

    public Optional<CartItem> findItem(Long productId) {
        return items.stream().filter(item -> item.getProductId().equals(productId)).findFirst();
    }

    public int quantityOf(Long productId) {
        return findItem(productId).map(CartItem::getQuantity).orElse(0);
    }

    public List<Long> productIds() {
        return items.stream().map(CartItem::getProductId).toList();
    }

    /**
     * Adds the product, or increases its quantity when it is already in the cart
     * (the item then also takes the latest name and prices from newItem).
     *
     * @param productStoreId the store selling the product
     */
    public void addItem(Long productStoreId, CartItem newItem) {
        if (hasItemsFromAnotherStore(productStoreId)) {
            throw new CartStoreConflictException(
                    "Your cart has items from another store. An order can only contain items from one store: "
                            + "place that order or empty the cart first (or add with replaceCart=true).");
        }

        Optional<CartItem> existingItem = findItem(newItem.getProductId());
        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            item.changeQuantity(item.getQuantity() + newItem.getQuantity());
            item.copyProductDetailsFrom(newItem);
        } else {
            if (items.size() >= MAX_DIFFERENT_ITEMS) {
                throw new BusinessException("A cart can hold at most " + MAX_DIFFERENT_ITEMS + " different products");
            }
            newItem.setCart(this);
            items.add(newItem);
        }
        this.storeId = productStoreId;
    }

    public void changeQuantity(Long productId, int quantity) {
        getItem(productId).changeQuantity(quantity);
    }

    public void removeItem(Long productId) {
        items.remove(getItem(productId));
        if (items.isEmpty()) {
            clear();
        }
    }

    /**
     * After an order was placed: removes the ordered products. Products the customer added after
     * placing the order stay in the cart.
     */
    public void removeOrderedItems(Collection<Long> orderedProductIds) {
        items.removeIf(item -> orderedProductIds.contains(item.getProductId()));
        if (items.isEmpty()) {
            clear();
        }
    }

    /** Empties the cart; it can then take items from any store again. */
    public void clear() {
        items.clear();
        storeId = null;
        couponCode = null;
    }

    public void applyCoupon(String code) {
        if (isEmpty()) {
            throw new BusinessException("Add items to your cart before applying a coupon");
        }
        this.couponCode = code;
    }

    public void removeCoupon() {
        this.couponCode = null;
    }

    private CartItem getItem(Long productId) {
        return findItem(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product " + productId + " is not in your cart"));
    }
}
