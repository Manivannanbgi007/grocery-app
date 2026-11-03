package com.grocery.cart.service.impl;

import com.grocery.cart.client.ProductCatalogClient;
import com.grocery.cart.client.dto.CatalogProduct;
import com.grocery.cart.dto.request.AddCartItemRequest;
import com.grocery.cart.dto.request.ApplyCouponRequest;
import com.grocery.cart.dto.request.UpdateCartItemRequest;
import com.grocery.cart.dto.response.CartResponse;
import com.grocery.cart.entity.Cart;
import com.grocery.cart.entity.Coupon;
import com.grocery.cart.exception.BusinessException;
import com.grocery.cart.exception.ResourceNotFoundException;
import com.grocery.cart.mapper.CartMapper;
import com.grocery.cart.pricing.AppliedCoupon;
import com.grocery.cart.pricing.CartPricingCalculator;
import com.grocery.cart.pricing.CouponEligibilityChecker;
import com.grocery.cart.repository.CartRepository;
import com.grocery.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final ProductCatalogClient productCatalogClient;
    private final CartMapper cartMapper;
    private final CartPricingCalculator pricingCalculator;
    private final CouponEligibilityChecker couponEligibilityChecker;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(Long customerUserId) {
        return cartRepository.findByCustomerUserId(customerUserId)
                .map(this::toResponse)
                .orElseGet(() -> emptyCart(customerUserId));
    }

    /**
     * Not @Transactional on purpose: product-read-service is asked first, WITHOUT a database transaction open (a slow
     * service must not hold a database connection). Only the cart change runs in a (short) transaction.
     */
    @Override
    public CartResponse addItem(Long customerUserId, AddCartItemRequest request, boolean replaceCart) {
        CatalogProduct product = findSellableProduct(request.productId());

        return transactionTemplate.execute(status -> {
            Cart cart = cartRepository.findByCustomerUserId(customerUserId)
                    .orElseGet(() -> Cart.newCart(customerUserId));

            if (replaceCart && cart.hasItemsFromAnotherStore(product.storeId())) {
                cart.clear();
                log.info("Customer id={} emptied their cart to buy from store id={}", customerUserId, product.storeId());
            }

            checkEnoughStock(product, cart.quantityOf(product.id()) + request.quantity());
            cart.addItem(product.storeId(), cartMapper.toCartItem(product, request.quantity()));
            Cart savedCart = cartRepository.save(cart);
            log.info("Customer id={} added {} x product id={}", customerUserId, request.quantity(), product.id());
            return toResponse(savedCart);
        });
    }

    /** Like addItem: the call to product-read-service (only needed for MORE units) happens between two short transactions. */
    @Override
    public CartResponse updateItemQuantity(Long customerUserId, Long productId, UpdateCartItemRequest request) {
        Integer quantityNow = transactionTemplate.execute(status -> findCart(customerUserId).quantityOf(productId));
        CatalogProduct product = request.quantity() > quantityNow ? findSellableProduct(productId) : null;

        return transactionTemplate.execute(status -> {
            Cart cart = findCart(customerUserId);
            if (product != null) {
                checkEnoughStock(product, request.quantity());
            }
            cart.changeQuantity(productId, request.quantity());
            return toResponse(cart);
        });
    }

    @Override
    @Transactional
    public CartResponse removeItem(Long customerUserId, Long productId) {
        Cart cart = findCart(customerUserId);
        cart.removeItem(productId);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse clearCart(Long customerUserId) {
        return cartRepository.findByCustomerUserId(customerUserId)
                .map(cart -> {
                    cart.clear();
                    return toResponse(cart);
                })
                .orElseGet(() -> emptyCart(customerUserId));
    }

    @Override
    @Transactional
    public CartResponse applyCoupon(Long customerUserId, ApplyCouponRequest request) {
        Cart cart = findCart(customerUserId);
        if (cart.isEmpty()) {
            throw new BusinessException("Add items to your cart before applying a coupon");
        }
        Coupon coupon = couponEligibilityChecker.checkUsable(
                request.code(), customerUserId, cart.getStoreId(), pricingCalculator.itemsTotal(cart.getItems()));
        cart.applyCoupon(coupon.getCode());
        log.info("Customer id={} applied coupon {}", customerUserId, coupon.getCode());
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse removeCoupon(Long customerUserId) {
        Cart cart = findCart(customerUserId);
        cart.removeCoupon();
        return toResponse(cart);
    }

    /** Totals with the coupon checked again: the cart may have changed since the coupon was applied. */
    private CartResponse toResponse(Cart cart) {
        BigDecimal itemsTotal = pricingCalculator.itemsTotal(cart.getItems());
        AppliedCoupon coupon = couponEligibilityChecker.evaluate(cart, itemsTotal);
        return cartMapper.toResponse(cart, pricingCalculator.calculate(cart.getItems(), coupon.discount()), coupon.message());
    }

    private CartResponse emptyCart(Long customerUserId) {
        return cartMapper.emptyCart(customerUserId, pricingCalculator.calculate(List.of(), BigDecimal.ZERO));
    }

    private Cart findCart(Long customerUserId) {
        return cartRepository.findByCustomerUserId(customerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Your cart is empty. Add a product first."));
    }

    /** The product as product-read-service knows it now; only ACTIVE products can be bought. */
    private CatalogProduct findSellableProduct(Long productId) {
        CatalogProduct product = productCatalogClient.findProduct(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        if (!product.isActive()) {
            throw new BusinessException(product.name() + " is not available at the moment");
        }
        return product;
    }

    /**
     * A first check only: the stock number in search can be a second old. The real guarantee comes
     * when the order is placed and inventory-service reserves the units (Phase 7).
     */
    private void checkEnoughStock(CatalogProduct product, int wantedQuantity) {
        if (product.availableQuantity() < wantedQuantity) {
            throw new BusinessException(product.availableQuantity() == 0
                    ? product.name() + " is out of stock"
                    : "Only %d unit(s) of %s are available".formatted(product.availableQuantity(), product.name()));
        }
    }
}
