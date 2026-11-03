package com.grocery.cart.service.impl;

import com.grocery.cart.client.ProductCatalogClient;
import com.grocery.cart.dto.request.AddCartItemRequest;
import com.grocery.cart.dto.request.ApplyCouponRequest;
import com.grocery.cart.dto.response.CartResponse;
import com.grocery.cart.entity.Cart;
import com.grocery.cart.exception.BusinessException;
import com.grocery.cart.exception.CartStoreConflictException;
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
import java.util.Optional;

import static com.grocery.cart.support.TestData.NOW;
import static com.grocery.cart.support.TestData.flatCoupon;
import static com.grocery.cart.support.TestData.product;
import static com.grocery.cart.support.TestData.productWithStatus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

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
    private CartServiceImpl cartService;

    @BeforeEach
    void setUp() {
        CartPricingCalculator calculator = new CartPricingCalculator(
                () -> new PricingRules(new BigDecimal("30.00"), new BigDecimal("299.00"), new BigDecimal("99.00")));
        CouponEligibilityChecker checker = new CouponEligibilityChecker(
                couponRepository, couponRedemptionRepository, Clock.fixed(NOW, ZoneOffset.UTC));
        cartService = new CartServiceImpl(cartRepository, productCatalogClient, cartMapper, calculator, checker,
                new TransactionTemplate(mock(PlatformTransactionManager.class)));   // runs the callback, no real database
    }

    @Test
    void addItem_createsTheCart_forTheFirstProduct() {
        when(productCatalogClient.findProduct(1L)).thenReturn(Optional.of(product(1, 1, "Tomato", "36.00", 50)));
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CartResponse cart = cartService.addItem(CUSTOMER, new AddCartItemRequest(1L, 3), false);

        assertThat(cart.storeId()).isEqualTo(1L);
        assertThat(cart.totals().itemsTotal()).isEqualByComparingTo("108.00");
        assertThat(cart.totals().payableAmount()).isEqualByComparingTo("138.00");   // + 30.00 delivery
    }

    @Test
    void addItem_productOfAnotherStore_is409_unlessReplaceCartIsSet() {
        Cart cart = cartWith(product(1, 1, "Tomato", "36.00", 50), 2);
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(productCatalogClient.findProduct(11L)).thenReturn(Optional.of(product(11, 2, "Spinach", "30.00", 50)));

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER, new AddCartItemRequest(11L, 1), false))
                .isInstanceOf(CartStoreConflictException.class);

        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CartResponse replaced = cartService.addItem(CUSTOMER, new AddCartItemRequest(11L, 1), true);
        assertThat(replaced.storeId()).isEqualTo(2L);
        assertThat(replaced.items()).extracting(item -> item.productName()).containsExactly("Spinach");
    }

    @Test
    void addItem_moreThanAvailable_isRejected_countingWhatIsAlreadyInTheCart() {
        Cart cart = cartWith(product(7, 1, "Amul Butter", "56.00", 3), 2);
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(productCatalogClient.findProduct(7L)).thenReturn(Optional.of(product(7, 1, "Amul Butter", "56.00", 3)));

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER, new AddCartItemRequest(7L, 2), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Only 3 unit(s) of Amul Butter are available");
        verify(cartRepository, never()).save(any());
    }

    @Test
    void addItem_inactiveProduct_cannotBeBought() {
        when(productCatalogClient.findProduct(17L)).thenReturn(Optional.of(productWithStatus(17, 1, "Mango Alphonso", "INACTIVE")));

        assertThatThrownBy(() -> cartService.addItem(CUSTOMER, new AddCartItemRequest(17L, 1), false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Mango Alphonso is not available at the moment");
    }

    @Test
    void applyCoupon_belowItsMinimum_isRejected_andNotStored() {
        Cart cart = cartWith(product(1, 1, "Tomato", "36.00", 50), 2);   // 72.00
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(couponRepository.findByCode("WELCOME50")).thenReturn(Optional.of(flatCoupon("WELCOME50", "50.00", "299.00")));

        assertThatThrownBy(() -> cartService.applyCoupon(CUSTOMER, new ApplyCouponRequest("welcome50")))
                .isInstanceOf(BusinessException.class)
                .hasMessageStartingWith("Coupon WELCOME50 needs an items total of at least 299.00");
        assertThat(cart.getCouponCode()).isNull();
    }

    @Test
    void getCart_showsTheCouponDiscount_andExplainsWhenItStopsWorking() {
        Cart cart = cartWith(product(5, 1, "Apple", "162.00", 50), 2);   // 324.00
        cart.applyCoupon("WELCOME50");
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.of(cart));
        when(couponRepository.findByCode("WELCOME50")).thenReturn(Optional.of(flatCoupon("WELCOME50", "50.00", "299.00")));

        CartResponse withCoupon = cartService.getCart(CUSTOMER);
        assertThat(withCoupon.totals().couponDiscount()).isEqualByComparingTo("50.00");
        assertThat(withCoupon.couponMessage()).isEqualTo("Coupon WELCOME50 applied: you save 50.00");

        cart.changeQuantity(5L, 1);   // 162.00: below the coupon's minimum now
        CartResponse below = cartService.getCart(CUSTOMER);
        assertThat(below.totals().couponDiscount()).isEqualByComparingTo("0.00");
        assertThat(below.couponMessage()).startsWith("Coupon WELCOME50 needs an items total of at least 299.00");
    }

    @Test
    void getCart_withoutACart_returnsAnEmptyCart() {
        when(cartRepository.findByCustomerUserId(CUSTOMER)).thenReturn(Optional.empty());

        CartResponse cart = cartService.getCart(CUSTOMER);

        assertThat(cart.items()).isEmpty();
        assertThat(cart.totals().payableAmount()).isEqualByComparingTo("0.00");
    }

    private Cart cartWith(com.grocery.cart.client.dto.CatalogProduct product, int quantity) {
        Cart cart = Cart.newCart(CUSTOMER);
        cart.addItem(product.storeId(), cartMapper.toCartItem(product, quantity));
        return cart;
    }
}
