package com.grocery.cart.entity;

import com.grocery.cart.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static com.grocery.cart.support.TestData.NOW;
import static com.grocery.cart.support.TestData.flatCoupon;
import static com.grocery.cart.support.TestData.percentageCoupon;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The coupon rules (plain JUnit).
 */
class CouponTest {

    @Test
    void percentageDiscount_isCappedByTheMaximum() {
        Coupon save10 = percentageCoupon("SAVE10", "10", "100.00", "199.00");

        assertThat(save10.discountFor(new BigDecimal("201.00"))).isEqualByComparingTo("20.10");
        assertThat(save10.discountFor(new BigDecimal("2500.00"))).isEqualByComparingTo("100.00");
    }

    @Test
    void flatDiscount_isNeverMoreThanTheItemsTotal() {
        Coupon flat = flatCoupon("FLAT500", "500.00", "0.00");

        assertThat(flat.discountFor(new BigDecimal("120.00"))).isEqualByComparingTo("120.00");
    }

    @Test
    void usableCoupon_passesEveryCheck() {
        Coupon welcome = flatCoupon("WELCOME50", "50.00", "299.00");

        assertThatNoException().isThrownBy(() -> welcome.checkCanBeUsed(1L, new BigDecimal("300.00"), NOW, 0));
    }

    @Test
    void itemsTotalBelowTheMinimum_saysHowMuchIsMissing() {
        Coupon welcome = flatCoupon("WELCOME50", "50.00", "299.00");

        assertThatThrownBy(() -> welcome.checkCanBeUsed(1L, new BigDecimal("201.00"), NOW, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Coupon WELCOME50 needs an items total of at least 299.00 (add 98.00 more)");
    }

    @Test
    void expiredOrNotYetValid_isRejected() {
        Coupon coupon = flatCoupon("WELCOME50", "50.00", "0.00");

        assertThatThrownBy(() -> coupon.checkCanBeUsed(1L, BigDecimal.TEN, Instant.parse("2027-01-01T00:00:00Z"), 0))
                .hasMessage("Coupon WELCOME50 has expired");
        assertThatThrownBy(() -> coupon.checkCanBeUsed(1L, BigDecimal.TEN, Instant.parse("2025-12-31T00:00:00Z"), 0))
                .hasMessage("Coupon WELCOME50 is not valid yet");
    }

    @Test
    void storeCoupon_onlyWorksInItsStore() {
        Coupon fresh20 = percentageCoupon("FRESH20", "20", "60.00", "0.00");
        fresh20.setStoreId(1L);

        assertThatNoException().isThrownBy(() -> fresh20.checkCanBeUsed(1L, BigDecimal.TEN, NOW, 0));
        assertThatThrownBy(() -> fresh20.checkCanBeUsed(2L, BigDecimal.TEN, NOW, 0))
                .hasMessage("Coupon FRESH20 is only valid in another store");
    }

    @Test
    void inactiveFullyUsedOrAlreadyUsedByTheCustomer_isRejected() {
        Coupon coupon = flatCoupon("WELCOME50", "50.00", "0.00");
        assertThatThrownBy(() -> coupon.checkCanBeUsed(1L, BigDecimal.TEN, NOW, 1))
                .hasMessage("You have already used coupon WELCOME50");

        coupon.setUsageLimit(10);
        coupon.setUsedCount(10);
        assertThatThrownBy(() -> coupon.checkCanBeUsed(1L, BigDecimal.TEN, NOW, 0))
                .hasMessage("Coupon WELCOME50 has been fully used");

        coupon.setActive(false);
        assertThatThrownBy(() -> coupon.checkCanBeUsed(1L, BigDecimal.TEN, NOW, 0))
                .hasMessage("Coupon WELCOME50 is not active");
    }

    @Test
    void checkSettings_rejectsImpossibleValues() {
        Coupon tooMuch = percentageCoupon("HUGE", "150", null, "0.00");
        assertThatThrownBy(tooMuch::checkSettings).hasMessage("A percentage discount cannot be more than 100");

        Coupon backwards = flatCoupon("BACKWARDS", "10.00", "0.00");
        backwards.setValidUntil(backwards.getValidFrom().minusSeconds(60));
        assertThatThrownBy(backwards::checkSettings).hasMessage("validUntil must be after validFrom");
    }
}
