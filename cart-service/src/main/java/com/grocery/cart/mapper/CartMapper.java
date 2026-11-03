package com.grocery.cart.mapper;

import com.grocery.cart.client.dto.CatalogProduct;
import com.grocery.cart.dto.response.CartItemResponse;
import com.grocery.cart.dto.response.CartResponse;
import com.grocery.cart.dto.response.CartTotalsResponse;
import com.grocery.cart.dto.response.CheckoutSummaryResponse;
import com.grocery.cart.entity.Cart;
import com.grocery.cart.entity.CartItem;
import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.util.List;

@Component
public class CartMapper {

    /** A new cart item with a copy of the product's current details. */
    public CartItem toCartItem(CatalogProduct product, int quantity) {
        CartItem item = new CartItem();
        item.setProductId(product.id());
        item.changeQuantity(quantity);
        copyProductDetails(product, item);
        return item;
    }

    /** Refreshes an item with the product's current name and prices. */
    public void copyProductDetails(CatalogProduct product, CartItem item) {
        item.setProductName(product.name());
        item.setUnit(product.unit());
        item.setUnitQuantity(product.unitQuantity());
        item.setImageUrl(product.firstImageUrl());
        item.setPrice(product.price());
        item.setSellingPrice(product.sellingPrice());
        item.setTaxPercentage(product.taxPercentage());
    }

    public CartResponse toResponse(Cart cart, CartTotalsResponse totals, String couponMessage) {
        return new CartResponse(
                cart.getCustomerUserId(),
                cart.getStoreId(),
                toItemResponses(cart.getItems()),
                cart.getItems().size(),
                cart.getCouponCode(),
                couponMessage,
                totals);
    }

    /** What a customer without a cart sees. */
    public CartResponse emptyCart(Long customerUserId, CartTotalsResponse totals) {
        return new CartResponse(customerUserId, null, List.of(), 0, null, null, totals);
    }

    /**
     * @param couponCode the coupon whose discount is in the totals (null = none)
     */
    public CheckoutSummaryResponse toCheckoutSummary(Long customerUserId, Long storeId, List<CartItem> items, String couponCode,
                                                     CartTotalsResponse totals, List<String> problems) {
        List<CartItemResponse> itemResponses = toItemResponses(items);
        return new CheckoutSummaryResponse(customerUserId, storeId, itemResponses, couponCode, totals,
                problems.isEmpty(), problems, CheckoutFingerprint.of(storeId, itemResponses, couponCode, totals));
    }

    private List<CartItemResponse> toItemResponses(List<CartItem> items) {
        return items.stream().map(this::toItemResponse).toList();
    }

    private CartItemResponse toItemResponse(CartItem item) {
        return new CartItemResponse(
                item.getProductId(),
                item.getProductName(),
                item.getUnit(),
                item.getUnitQuantity().stripTrailingZeros(),   // 500.000 -> 500
                item.getImageUrl(),
                item.getPrice(),
                item.getSellingPrice(),
                item.getTaxPercentage(),
                item.getQuantity(),
                item.lineTotal().setScale(2, RoundingMode.HALF_UP));
    }
}
