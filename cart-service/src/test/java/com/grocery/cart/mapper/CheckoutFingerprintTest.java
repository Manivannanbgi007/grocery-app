package com.grocery.cart.mapper;

import com.grocery.cart.dto.response.CartItemResponse;
import com.grocery.cart.dto.response.CartTotalsResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The fingerprint of a checkout changes with everything an order would contain, and with nothing else. */
class CheckoutFingerprintTest {

    private static final CartItemResponse TOMATO = item(1L, "36.00", 2);
    private static final CartItemResponse BUTTER = item(7L, "56.00", 1);
    private static final CartTotalsResponse TOTALS = totals("128.00", "0.00", "30.00", "158.00");

    @Test
    void theSameCartGivesTheSameShortFingerprint_inAnyOrderAndWithAnyScale() {
        String fingerprint = CheckoutFingerprint.of(1L, List.of(TOMATO, BUTTER), null, TOTALS);

        assertThat(fingerprint).hasSize(16).matches("[0-9a-f]+");
        assertThat(CheckoutFingerprint.of(1L, List.of(BUTTER, TOMATO), null, TOTALS)).isEqualTo(fingerprint);
        assertThat(CheckoutFingerprint.of(1L, List.of(item(1L, "36", 2), item(7L, "56.0", 1)), null,
                totals("128", "0", "30", "158"))).isEqualTo(fingerprint);
    }

    @Test
    void aQuantityAPriceACouponOrAnAmountChangesIt() {
        String fingerprint = CheckoutFingerprint.of(1L, List.of(TOMATO, BUTTER), null, TOTALS);

        assertThat(CheckoutFingerprint.of(1L, List.of(item(1L, "36.00", 3), BUTTER), null, TOTALS)).isNotEqualTo(fingerprint);
        assertThat(CheckoutFingerprint.of(1L, List.of(item(1L, "38.00", 2), BUTTER), null, TOTALS)).isNotEqualTo(fingerprint);
        assertThat(CheckoutFingerprint.of(1L, List.of(TOMATO, BUTTER), "SAVE10", TOTALS)).isNotEqualTo(fingerprint);
        assertThat(CheckoutFingerprint.of(1L, List.of(TOMATO, BUTTER), null, totals("128.00", "0.00", "0.00", "128.00")))
                .isNotEqualTo(fingerprint);
        assertThat(CheckoutFingerprint.of(2L, List.of(TOMATO, BUTTER), null, TOTALS)).isNotEqualTo(fingerprint);
        assertThat(CheckoutFingerprint.of(1L, List.of(TOMATO), null, TOTALS)).isNotEqualTo(fingerprint);
    }

    private static CartItemResponse item(Long productId, String sellingPrice, int quantity) {
        BigDecimal price = new BigDecimal(sellingPrice);
        return new CartItemResponse(productId, "Product " + productId, "G", new BigDecimal("100"), null, price, price,
                BigDecimal.ZERO, quantity, price.multiply(BigDecimal.valueOf(quantity)));
    }

    private static CartTotalsResponse totals(String items, String couponDiscount, String deliveryFee, String payable) {
        return new CartTotalsResponse(new BigDecimal(items), BigDecimal.ZERO, new BigDecimal(couponDiscount),
                new BigDecimal(deliveryFee), new BigDecimal(payable), BigDecimal.ZERO, new BigDecimal("99"), true);
    }
}
