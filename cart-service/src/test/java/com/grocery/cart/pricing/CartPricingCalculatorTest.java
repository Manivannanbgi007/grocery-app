package com.grocery.cart.pricing;

import com.grocery.cart.client.dto.CatalogProduct;
import com.grocery.cart.dto.response.CartTotalsResponse;
import com.grocery.cart.entity.CartItem;
import com.grocery.cart.mapper.CartMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static com.grocery.cart.support.TestData.product;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Delivery fee 30.00, free delivery from 299.00, minimum order 99.00.
 */
class CartPricingCalculatorTest {

    private final CartPricingCalculator calculator = new CartPricingCalculator(
            () -> new PricingRules(new BigDecimal("30.00"), new BigDecimal("299.00"), new BigDecimal("99.00")));
    private final CartMapper cartMapper = new CartMapper();

    @Test
    void smallCart_paysTheDeliveryFee_minusTheCoupon() {
        List<CartItem> items = List.of(item(product(1, 1, "Tomato", "36.00", 50), 2), item(product(6, 1, "Milk", "27.00", 50), 3));

        CartTotalsResponse totals = calculator.calculate(items, new BigDecimal("15.30"));

        assertThat(totals.itemsTotal()).isEqualByComparingTo("153.00");
        assertThat(totals.deliveryFee()).isEqualByComparingTo("30.00");
        assertThat(totals.payableAmount()).isEqualByComparingTo("167.70");   // 153.00 - 15.30 + 30.00
        assertThat(totals.minimumOrderMet()).isTrue();
    }

    @Test
    void deliveryIsFree_fromTheFreeDeliveryAmount() {
        CartTotalsResponse totals = calculator.calculate(List.of(item(product(5, 1, "Apple", "162.00", 50), 2)), BigDecimal.ZERO);

        assertThat(totals.itemsTotal()).isEqualByComparingTo("324.00");
        assertThat(totals.deliveryFee()).isEqualByComparingTo("0.00");
        assertThat(totals.payableAmount()).isEqualByComparingTo("324.00");
    }

    @Test
    void productDiscountsAndIncludedTax_areShown() {
        // Surf Excel: MRP 230.00, selling 195.50, 18 % GST included
        CatalogProduct surf = new CatalogProduct(10L, 1L, "Surf Excel", "L", BigDecimal.ONE, new BigDecimal("230.00"),
                new BigDecimal("195.50"), new BigDecimal("18.00"), List.of(), "ACTIVE", 20);

        CartTotalsResponse totals = calculator.calculate(List.of(item(surf, 1)), BigDecimal.ZERO);

        assertThat(totals.productSavings()).isEqualByComparingTo("34.50");
        assertThat(totals.taxIncluded()).isEqualByComparingTo("29.82");     // 195.50 x 18 / 118
    }

    @Test
    void cartBelowTheMinimumOrderValue_cannotBeOrdered() {
        CartTotalsResponse totals = calculator.calculate(List.of(item(product(9, 1, "Chips", "20.00", 50), 2)), BigDecimal.ZERO);

        assertThat(totals.minimumOrderMet()).isFalse();
        assertThat(totals.minimumOrderValue()).isEqualByComparingTo("99.00");
    }

    @Test
    void emptyCart_costsNothing() {
        CartTotalsResponse totals = calculator.calculate(List.of(), BigDecimal.ZERO);

        assertThat(totals.payableAmount()).isEqualByComparingTo("0.00");
        assertThat(totals.deliveryFee()).isEqualByComparingTo("0.00");
    }

    private CartItem item(CatalogProduct product, int quantity) {
        return cartMapper.toCartItem(product, quantity);
    }
}
