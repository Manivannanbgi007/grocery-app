package com.grocery.cart.pricing;

import com.grocery.cart.dto.response.CartTotalsResponse;
import com.grocery.cart.entity.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Calculates the totals of a cart. All prices include tax.
 *
 *   itemsTotal     = sum of sellingPrice x quantity
 *   deliveryFee    = 0 for an empty cart or when itemsTotal >= freeDeliveryFrom, otherwise the delivery fee
 *   payableAmount  = itemsTotal - couponDiscount + deliveryFee
 *   taxIncluded    = the GST inside the prices: lineTotal x tax / (100 + tax)
 */
@Component
@RequiredArgsConstructor
public class CartPricingCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PricingRulesProvider pricingRulesProvider;

    public BigDecimal itemsTotal(List<CartItem> items) {
        return items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * @param couponDiscount the discount of a usable coupon, or 0
     */
    public CartTotalsResponse calculate(List<CartItem> items, BigDecimal couponDiscount) {
        BigDecimal itemsTotal = itemsTotal(items);
        BigDecimal totalBeforeProductDiscounts = items.stream()
                .map(CartItem::lineTotalBeforeDiscount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal productSavings = totalBeforeProductDiscounts.subtract(itemsTotal).setScale(2, RoundingMode.HALF_UP);

        PricingRules rules = pricingRulesProvider.current();
        boolean freeDelivery = items.isEmpty() || itemsTotal.compareTo(rules.freeDeliveryFrom()) >= 0;
        BigDecimal deliveryFee = freeDelivery ? BigDecimal.ZERO : rules.deliveryFee();

        BigDecimal payableAmount = itemsTotal.subtract(couponDiscount).add(deliveryFee).setScale(2, RoundingMode.HALF_UP);
        boolean minimumOrderMet = itemsTotal.compareTo(rules.minimumOrderValue()) >= 0;

        return new CartTotalsResponse(
                itemsTotal,
                productSavings,
                couponDiscount.setScale(2, RoundingMode.HALF_UP),
                deliveryFee.setScale(2, RoundingMode.HALF_UP),
                payableAmount,
                taxIncluded(items),
                rules.minimumOrderValue().setScale(2, RoundingMode.HALF_UP),
                minimumOrderMet);
    }

    private BigDecimal taxIncluded(List<CartItem> items) {
        return items.stream()
                .map(item -> item.lineTotal()
                        .multiply(item.getTaxPercentage())
                        .divide(HUNDRED.add(item.getTaxPercentage()), 2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
