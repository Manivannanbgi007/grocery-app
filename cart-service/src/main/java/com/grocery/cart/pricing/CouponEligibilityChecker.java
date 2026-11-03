package com.grocery.cart.pricing;

import com.grocery.cart.entity.Cart;
import com.grocery.cart.entity.Coupon;
import com.grocery.cart.exception.BusinessException;
import com.grocery.cart.exception.ResourceNotFoundException;
import com.grocery.cart.repository.CouponRedemptionRepository;
import com.grocery.cart.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;

/**
 * Decides whether a customer may use a coupon on their cart (the rules themselves are in Coupon).
 */
@Component
@RequiredArgsConstructor
public class CouponEligibilityChecker {

    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final Clock clock;

    /**
     * Finds the coupon and checks every rule.
     *
     * @throws ResourceNotFoundException when the code does not exist
     * @throws BusinessException         with the reason when the coupon cannot be used
     */
    public Coupon checkUsable(String code, Long customerUserId, Long cartStoreId, BigDecimal itemsTotal) {
        Coupon coupon = couponRepository.findByCode(code.toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Coupon " + code + " does not exist"));
        long timesUsedByCustomer = couponRedemptionRepository.countByCouponIdAndCustomerUserId(coupon.getId(), customerUserId);
        coupon.checkCanBeUsed(cartStoreId, itemsTotal, Instant.now(clock), timesUsedByCustomer);
        return coupon;
    }

    /**
     * The coupon already in the cart, checked again (the cart or the coupon may have changed since it was applied).
     * Never throws: a coupon that cannot be used gives 0 discount and a problem text.
     */
    public AppliedCoupon evaluate(Cart cart, BigDecimal itemsTotal) {
        if (cart.getCouponCode() == null) {
            return AppliedCoupon.none();
        }
        try {
            Coupon coupon = checkUsable(cart.getCouponCode(), cart.getCustomerUserId(), cart.getStoreId(), itemsTotal);
            return new AppliedCoupon(coupon.getCode(), coupon.discountFor(itemsTotal), null);
        } catch (BusinessException | ResourceNotFoundException ex) {
            return new AppliedCoupon(cart.getCouponCode(), BigDecimal.ZERO, ex.getMessage());
        }
    }
}
