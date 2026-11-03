package com.grocery.cart.service.impl;

import com.grocery.cart.client.ProductCatalogClient;
import com.grocery.cart.client.dto.CatalogProduct;
import com.grocery.cart.dto.response.CartTotalsResponse;
import com.grocery.cart.dto.response.CheckoutSummaryResponse;
import com.grocery.cart.entity.Cart;
import com.grocery.cart.entity.CartItem;
import com.grocery.cart.mapper.CartMapper;
import com.grocery.cart.pricing.AppliedCoupon;
import com.grocery.cart.pricing.CartPricingCalculator;
import com.grocery.cart.pricing.CouponEligibilityChecker;
import com.grocery.cart.repository.CartRepository;
import com.grocery.cart.service.CheckoutService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {

    private final CartRepository cartRepository;
    private final ProductCatalogClient productCatalogClient;
    private final CartMapper cartMapper;
    private final CartPricingCalculator pricingCalculator;
    private final CouponEligibilityChecker couponEligibilityChecker;
    private final TransactionTemplate transactionTemplate;

    /**
     * Problems are collected, not thrown: the customer sees everything to fix at once,
     * and the refreshed prices are saved in the cart either way.
     *
     * Not @Transactional on purpose: the call to product-read-service happens BETWEEN two short transactions, so a
     * slow service never holds a database connection.
     */
    @Override
    public CheckoutSummaryResponse prepareCheckout(Long customerUserId) {
        List<Long> productIds = transactionTemplate.execute(status -> cartRepository.findByCustomerUserId(customerUserId)
                .map(Cart::productIds).orElse(List.of()));
        if (productIds.isEmpty()) {
            CartTotalsResponse noTotals = pricingCalculator.calculate(List.of(), BigDecimal.ZERO);
            return cartMapper.toCheckoutSummary(customerUserId, null, List.of(), null, noTotals, List.of("Your cart is empty"));
        }

        // 1. Today's details of every product, in ONE call to product-read-service
        Map<Long, CatalogProduct> currentProducts = productCatalogClient.findProducts(productIds);

        // 2. Refresh and check the cart in a short transaction (the cart's @Version notices a parallel change)
        return transactionTemplate.execute(status -> checkCart(customerUserId, productIds, currentProducts));
    }

    private CheckoutSummaryResponse checkCart(Long customerUserId, List<Long> askedProductIds, Map<Long, CatalogProduct> currentProducts) {
        Cart cart = cartRepository.findByCustomerUserId(customerUserId).orElseThrow();
        List<String> problems = new ArrayList<>();

        for (CartItem item : cart.getItems()) {
            if (!askedProductIds.contains(item.getProductId())) {
                continue;   // added a moment ago, while we asked product-read-service: its details are seconds old
            }
            CatalogProduct product = currentProducts.get(item.getProductId());
            if (product == null || !product.isActive()) {
                problems.add(item.getProductName() + " is no longer available. Please remove it from your cart.");
                continue;
            }
            cartMapper.copyProductDetails(product, item);   // the order uses today's prices
            if (product.availableQuantity() < item.getQuantity()) {
                problems.add(product.availableQuantity() == 0
                        ? product.name() + " is out of stock"
                        : "Only %d unit(s) of %s are available".formatted(product.availableQuantity(), product.name()));
            }
        }

        // 3. The coupon, checked against the refreshed total
        BigDecimal itemsTotal = pricingCalculator.itemsTotal(cart.getItems());
        AppliedCoupon coupon = couponEligibilityChecker.evaluate(cart, itemsTotal);
        if (coupon.isPresent() && !coupon.isUsable()) {
            problems.add(coupon.problem() + ". Remove the coupon to continue.");
        }

        // 4. The minimum order value
        CartTotalsResponse totals = pricingCalculator.calculate(cart.getItems(), coupon.discount());
        if (!totals.minimumOrderMet()) {
            problems.add("The minimum order value is %s (add %s more)"
                    .formatted(totals.minimumOrderValue(), totals.minimumOrderValue().subtract(totals.itemsTotal())));
        }

        return cartMapper.toCheckoutSummary(customerUserId, cart.getStoreId(), cart.getItems(),
                coupon.isUsable() ? coupon.code() : null, totals, problems);
    }
}
