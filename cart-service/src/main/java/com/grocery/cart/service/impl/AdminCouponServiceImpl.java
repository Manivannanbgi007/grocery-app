package com.grocery.cart.service.impl;

import com.grocery.cart.dto.common.PageResponse;
import com.grocery.cart.dto.request.CouponRequest;
import com.grocery.cart.dto.response.CouponResponse;
import com.grocery.cart.entity.Coupon;
import com.grocery.cart.exception.BusinessException;
import com.grocery.cart.exception.DuplicateResourceException;
import com.grocery.cart.exception.ResourceNotFoundException;
import com.grocery.cart.mapper.CouponMapper;
import com.grocery.cart.repository.CouponRepository;
import com.grocery.cart.service.AdminCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminCouponServiceImpl implements AdminCouponService {

    private final CouponRepository couponRepository;
    private final CouponMapper couponMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> searchCoupons(Long storeId, boolean platformOnly, Pageable pageable) {
        return PageResponse.from(couponRepository.search(storeId, platformOnly, pageable).map(couponMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse getCoupon(Long couponId) {
        return couponMapper.toResponse(findCoupon(couponId));
    }

    @Override
    @Transactional
    public CouponResponse createPlatformCoupon(Long adminUserId, CouponRequest request) {
        checkCodeIsFree(couponMapper.normalizeCode(request.code()));
        Coupon coupon = couponMapper.toEntity(request, null, adminUserId);   // no store = platform coupon
        coupon.checkSettings();
        Coupon savedCoupon = couponRepository.save(coupon);
        log.info("Admin id={} created platform coupon {}", adminUserId, savedCoupon.getCode());
        return couponMapper.toResponse(savedCoupon);
    }

    @Override
    @Transactional
    public CouponResponse updatePlatformCoupon(Long couponId, CouponRequest request) {
        Coupon coupon = findCoupon(couponId);
        if (!coupon.isPlatformCoupon()) {
            throw new BusinessException("Store coupons are managed by their vendor. Admins can only activate or deactivate them.");
        }
        String newCode = couponMapper.normalizeCode(request.code());
        if (!newCode.equals(coupon.getCode())) {
            checkCodeIsFree(newCode);
        }
        couponMapper.copyDetails(request, coupon);
        coupon.checkSettings();
        return couponMapper.toResponse(coupon);
    }

    @Override
    @Transactional
    public CouponResponse changeStatus(Long couponId, boolean active) {
        Coupon coupon = findCoupon(couponId);
        coupon.setActive(active);
        log.info("Admin set coupon {} active={}", coupon.getCode(), active);
        return couponMapper.toResponse(coupon);
    }

    private Coupon findCoupon(Long couponId) {
        return couponRepository.findById(couponId).orElseThrow(() -> new ResourceNotFoundException("Coupon", couponId));
    }

    private void checkCodeIsFree(String code) {
        if (couponRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Coupon code " + code + " is already taken. Choose another code.");
        }
    }
}
