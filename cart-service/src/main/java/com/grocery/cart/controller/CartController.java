package com.grocery.cart.controller;

import com.grocery.cart.context.UserContextHolder;
import com.grocery.cart.dto.common.ApiResponse;
import com.grocery.cart.dto.request.AddCartItemRequest;
import com.grocery.cart.dto.request.ApplyCouponRequest;
import com.grocery.cart.dto.request.UpdateCartItemRequest;
import com.grocery.cart.dto.response.CartResponse;
import com.grocery.cart.dto.response.CheckoutSummaryResponse;
import com.grocery.cart.service.CartService;
import com.grocery.cart.service.CheckoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer panel: MY cart (the customer comes from X-User-Id).
 * Allowed role (checked by the API gateway): CUSTOMER.
 */
@Tag(name = "Cart", description = "Your shopping cart and coupon (CUSTOMER)")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;
    private final CheckoutService checkoutService;

    @Operation(summary = "My cart with totals")
    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        return ResponseEntity.ok(ApiResponse.success("Cart fetched successfully", cartService.getCart(currentCustomer())));
    }

    @Operation(summary = "Add a product (409 when the cart has items of another store, unless replaceCart=true)")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @Valid @RequestBody AddCartItemRequest request,
            @Parameter(description = "true = empty a cart of another store first")
            @RequestParam(defaultValue = "false") boolean replaceCart) {
        CartResponse cart = cartService.addItem(currentCustomer(), request, replaceCart);
        return ResponseEntity.ok(ApiResponse.success("Product added to cart", cart));
    }

    @Operation(summary = "Change the quantity of a product in my cart")
    @PutMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItemQuantity(@PathVariable Long productId,
                                                                        @Valid @RequestBody UpdateCartItemRequest request) {
        CartResponse cart = cartService.updateItemQuantity(currentCustomer(), productId, request);
        return ResponseEntity.ok(ApiResponse.success("Quantity updated", cart));
    }

    @Operation(summary = "Remove a product from my cart")
    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(@PathVariable Long productId) {
        return ResponseEntity.ok(ApiResponse.success("Product removed from cart", cartService.removeItem(currentCustomer(), productId)));
    }

    @Operation(summary = "Empty my cart")
    @DeleteMapping
    public ResponseEntity<ApiResponse<CartResponse>> clearCart() {
        return ResponseEntity.ok(ApiResponse.success("Cart emptied", cartService.clearCart(currentCustomer())));
    }

    @Operation(summary = "Apply a coupon code (one coupon per cart; a new code replaces the old one)")
    @PutMapping("/coupon")
    public ResponseEntity<ApiResponse<CartResponse>> applyCoupon(@Valid @RequestBody ApplyCouponRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Coupon applied", cartService.applyCoupon(currentCustomer(), request)));
    }

    @Operation(summary = "Remove the coupon")
    @DeleteMapping("/coupon")
    public ResponseEntity<ApiResponse<CartResponse>> removeCoupon() {
        return ResponseEntity.ok(ApiResponse.success("Coupon removed", cartService.removeCoupon(currentCustomer())));
    }

    @Operation(summary = "Checkout page: my cart with TODAY's prices and anything that blocks the order")
    @GetMapping("/checkout")
    public ResponseEntity<ApiResponse<CheckoutSummaryResponse>> checkout() {
        return ResponseEntity.ok(ApiResponse.success("Checkout summary prepared", checkoutService.prepareCheckout(currentCustomer())));
    }

    private Long currentCustomer() {
        return UserContextHolder.getRequiredUserId();
    }
}
