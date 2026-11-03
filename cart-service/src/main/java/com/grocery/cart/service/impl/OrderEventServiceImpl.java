package com.grocery.cart.service.impl;

import com.grocery.cart.entity.CouponRedemption;
import com.grocery.cart.repository.CartRepository;
import com.grocery.cart.repository.CouponRedemptionRepository;
import com.grocery.cart.repository.CouponRepository;
import com.grocery.cart.service.OrderEventService;
import com.grocery.cart.service.ProcessedEventService;
import com.grocery.events.order.OrderCreated;
import com.grocery.events.order.OrderLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventServiceImpl implements OrderEventService {

    private final CartRepository cartRepository;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository couponRedemptionRepository;
    private final ProcessedEventService processedEventService;
    private final Clock clock;

    @Override
    @Transactional
    public void handleOrderCreated(OrderCreated event) {
        if (!processedEventService.markAsProcessed(event.getEventId(), "OrderCreated")) {
            return;
        }
        Long customerUserId = event.getCustomerUserId();
        String couponCode = event.getCouponCode();

        // 1. The cart no longer needs the ordered products (anything added since stays)
        List<Long> orderedProductIds = event.getItems().stream().map(OrderLine::getProductId).toList();
        cartRepository.findByCustomerUserId(customerUserId).ifPresent(cart -> {
            cart.removeOrderedItems(orderedProductIds);
            if (couponCode != null && couponCode.equals(cart.getCouponCode())) {
                cart.removeCoupon();
            }
        });

        // 2. Count the coupon use (order_id is unique, so one order never counts twice)
        if (couponCode != null && !couponRedemptionRepository.existsByOrderId(event.getOrderId())) {
            couponRepository.findByCode(couponCode).ifPresent(coupon -> {
                couponRedemptionRepository.save(new CouponRedemption(null, coupon.getId(), customerUserId, event.getOrderId(),
                        new BigDecimal(event.getCouponDiscount()), Instant.now(clock)));
                coupon.recordUse();
            });
        }
        log.info("Order id={} placed: cart of customer id={} updated{}", event.getOrderId(), customerUserId,
                couponCode == null ? "" : ", coupon " + couponCode + " used");
    }

    @Override
    @Transactional
    public void releaseCouponOfOrder(String eventId, String eventType, Long orderId) {
        if (!processedEventService.markAsProcessed(eventId, eventType)) {
            return;
        }
        couponRedemptionRepository.findByOrderId(orderId).ifPresent(redemption -> {
            couponRepository.findById(redemption.getCouponId()).ifPresent(coupon -> coupon.releaseUse());
            couponRedemptionRepository.delete(redemption);
            log.info("Order id={} {}: its coupon use no longer counts", orderId, eventType);
        });
    }
}
