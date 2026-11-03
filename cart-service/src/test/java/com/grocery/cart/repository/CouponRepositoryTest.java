package com.grocery.cart.repository;

import com.grocery.cart.TestcontainersConfiguration;
import com.grocery.cart.config.JpaAuditingConfig;
import com.grocery.cart.entity.Coupon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static com.grocery.cart.support.TestData.NOW;
import static com.grocery.cart.support.TestData.flatCoupon;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MySQL (Testcontainers) + real Flyway migrations: also proves the entities match the schema.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class CouponRepositoryTest {

    @Autowired
    private CouponRepository couponRepository;

    @BeforeEach
    void setUp() {
        couponRepository.save(coupon("PLATFORM10", null, true, null));
        couponRepository.save(coupon("STOREONE", 1L, true, null));
        couponRepository.save(coupon("STORETWO", 2L, true, null));
        couponRepository.save(coupon("SWITCHEDOFF", null, false, null));
        couponRepository.save(coupon("ENDED", null, true, Instant.parse("2026-06-30T00:00:00Z")));
    }

    @Test
    void findAvailable_forAStore_returnsPlatformCouponsAndThatStoresCoupons() {
        assertThat(couponRepository.findAvailable(1L, NOW, Sort.by("code")))
                .extracting(Coupon::getCode)
                .containsExactly("PLATFORM10", "STOREONE");
    }

    @Test
    void findAvailable_withoutAStore_returnsOnlyPlatformCoupons() {
        assertThat(couponRepository.findAvailable(null, NOW, Sort.by("code")))
                .extracting(Coupon::getCode)
                .containsExactly("PLATFORM10");
    }

    @Test
    void search_platformOnly_skipsStoreCoupons() {
        assertThat(couponRepository.search(null, true, PageRequest.of(0, 10, Sort.by("code"))).getContent())
                .extracting(Coupon::getCode)
                .containsExactly("ENDED", "PLATFORM10", "SWITCHEDOFF");
    }

    private Coupon coupon(String code, Long storeId, boolean active, Instant validUntil) {
        Coupon coupon = flatCoupon(code, "10.00", "0.00");
        coupon.setId(null);
        coupon.setStoreId(storeId);
        coupon.setActive(active);
        if (validUntil != null) {
            coupon.setValidUntil(validUntil);
        }
        return coupon;
    }
}
