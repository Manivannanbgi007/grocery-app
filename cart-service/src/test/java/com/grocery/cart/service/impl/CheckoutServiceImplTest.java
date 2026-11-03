package com.grocery.cart.service.impl;

import com.grocery.cart.client.ProductCatalogClient;
import com.grocery.cart.client.dto.CatalogProduct;
import com.grocery.cart.dto.response.CheckoutSummaryResponse;
import com.grocery.cart.entity.Cart;
import com.grocery.cart.mapper.CartMapper;
import com.grocery.cart.pricing.CartPricingCalculator;
import com.grocery.cart.pricing.PricingRules;
import com.grocery.cart.pricing.CouponEligibilityChecker;
import com.grocery.cart.repository.CartRepository;
import com.grocery.cart.repository.CouponRedemptionRepository;
import com.grocery.cart.repository.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.grocery.cart.support.TestData.NOW;
import static com.grocery.cart.support.TestData.percentageCoupon;
import static com.grocery.cart.support.TestData.product;
import static com.grocery.cart.support.TestData.productWithStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceImplTest {

    private static final Long CUSTOMER = 4L;

    @Mock
    private CartRepository cartRepository;
    @Mock
    private ProductCatalogClient productCatalogClient;
    @Mock
    private CouponRepository couponRepository;
    @Mock
    private CouponRedemptionRepository couponRedemptionRepository;

    private final CartMapper cartMapper = new CartMapper();
    private CheckoutServiceImpl checkoutService;

    @BeforeEach
    void setUp() {
        CartPricingCalculator calculator = new CartPricingCalculator(
                () -> new PricingRules(new BigDecimal("30.00"), new BigDecimal("299.00"), new BigDecimal("99.00")));
        CouponEligibilityChecker checker = new CouponEligibilityChecker(
                couponRepository, couponRedemptionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        checkoutService = new CheckoutServiceImpl(cartRepository, productCatalogClient, cartMapper, calculator, checker,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));   // runs the callback, no real database
    }

    @Test
    void readyCart_getsTodaysPrices_andTheCouponDiscount() {
        Cart cart = cartWith(product(1, 1, "Tomato", "36.00", 50), 5);   // added at 36.00
        cart.applyCoupon("SAVE10");
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(productCatalogClient.findProducts(List.of(1L))).thenReturn(Map.of(1L, product(1, 1, "Tomato", "40.00", 50)));
        when(couponRepository.findByCode("SAVE10")).thenReturn(Optional.of(percentageCoupon("SAVE10", "10", "100.00", "199.00")));

        CheckoutSummaryResponse summary = checkoutService.prepareCheckout(CUSTOMER);

        assertThat(summary.readyForCheckout()).isTrue();
        assertThat(summary.problems()).isEmpty();
        assertThat(summary.items().getFirst().sellingPrice()).isEqualByComparingTo("40.00");   // today's price
        assertThat(summary.totals().itemsTotal()).isEqualByComparingTo("200.00");
        assertThat(summary.totals().couponDiscount()).isEqualByComparingTo("20.00");
        assertThat(summary.totals().payableAmount()).isEqualByComparingTo("210.00");          // 200 - 20 + 30
        assertThat(summary.couponCode()).isEqualTo("SAVE10");
        assertThat(cart.getItems().getFirst().getSellingPrice()).isEqualByComparingTo("40.00"); // saved in the cart too
        assertThat(summary.checkoutFingerprint()).hasSize(16);
        assertThat(checkoutService.prepareCheckout(CUSTOMER).checkoutFingerprint())               // nothing changed: the same
                .isEqualTo(summary.checkoutFingerprint());
    }

    @Test
    void everyProblem_isListed_soTheCustomerCanFixThemAtOnce() {
        Cart cart = cartWith(product(1, 1, "Tomato", "36.00", 50), 3);
        cart.addItem(1L, cartMapper.toCartItem(product(7, 1, "Amul Butter", "56.00", 50), 4));
        cart.addItem(1L, cartMapper.toCartItem(product(17, 1, "Mango Alphonso", "350.00", 50), 1));
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(productCatalogClient.findProducts(List.of(1L, 7L, 17L))).thenReturn(Map.of(
                1L, product(1, 1, "Tomato", "36.00", 0),                         // sold out since
                7L, product(7, 1, "Amul Butter", "56.00", 3),                    // only 3 left
                17L, productWithStatus(17, 1, "Mango Alphonso", "INACTIVE")));   // switched off by the vendor

        CheckoutSummaryResponse summary = checkoutService.prepareCheckout(CUSTOMER);

        assertThat(summary.readyForCheckout()).isFalse();
        assertThat(summary.problems()).containsExactly(
                "Tomato is out of stock",
                "Only 3 unit(s) of Amul Butter are available",
                "Mango Alphonso is no longer available. Please remove it from your cart.");
    }

    @Test
    void cartBelowTheMinimumOrderValue_isNotReady() {
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.of(cartWith(product(9, 1, "Chips", "20.00", 50), 2)));
        when(productCatalogClient.findProducts(List.of(9L))).thenReturn(Map.of(9L, product(9, 1, "Chips", "20.00", 50)));

        CheckoutSummaryResponse summary = checkoutService.prepareCheckout(CUSTOMER);

        assertThat(summary.problems()).containsExactly("The minimum order value is 99.00 (add 59.00 more)");
    }

    @Test
    void noCart_isNotReady() {
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.empty());

        assertThat(checkoutService.prepareCheckout(CUSTOMER).problems()).containsExactly("Your cart is empty");
    }

    private Cart cartWith(CatalogProduct product, int quantity) {
        Cart cart = Cart.newCart(CUSTOMER);
        cart.addItem(product.storeId(), cartMapper.toCartItem(product, quantity));
        return cart;
    }
}
