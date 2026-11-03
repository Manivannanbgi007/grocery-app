package com.grocery.cart.service.impl;

import com.grocery.cart.dto.common.PageResponse;
import com.grocery.cart.dto.request.CouponRequest;
import com.grocery.cart.dto.response.CouponResponse;
import com.grocery.cart.entity.Coupon;
import com.grocery.cart.exception.DuplicateResourceException;
import com.grocery.cart.exception.ResourceNotFoundException;
import com.grocery.cart.mapper.CouponMapper;
import com.grocery.cart.repository.CouponRepository;
import com.grocery.cart.service.VendorCouponService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class VendorCouponServiceImpl implements VendorCouponService {

    private final CouponRepository couponRepository;
    private final CouponMapper couponMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CouponResponse> listMyCoupons(Long storeId, Pageable pageable) {
        return PageResponse.from(couponRepository.findByStoreId(storeId, pageable).map(couponMapper::toResponse));
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse getMyCoupon(Long storeId, Long couponId) {
        return couponMapper.toResponse(findMyCoupon(storeId, couponId));
    }

    @Override
    @Transactional
    public CouponResponse createCoupon(Long storeId, Long vendorUserId, CouponRequest request) {
        checkCodeIsFree(couponMapper.normalizeCode(request.code()));
        Coupon coupon = couponMapper.toEntity(request, storeId, vendorUserId);
        coupon.checkSettings();
        Coupon savedCoupon = couponRepository.save(coupon);
        log.info("Store id={} created coupon {}", storeId, savedCoupon.getCode());
        return couponMapper.toResponse(savedCoupon);
    }

    @Override
    @Transactional
    public CouponResponse updateCoupon(Long storeId, Long couponId, CouponRequest request) {
        Coupon coupon = findMyCoupon(storeId, couponId);
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
    public CouponResponse changeStatus(Long storeId, Long couponId, boolean active) {
        Coupon coupon = findMyCoupon(storeId, couponId);
        coupon.setActive(active);
        log.info("Store id={} set coupon {} active={}", storeId, coupon.getCode(), active);
        return couponMapper.toResponse(coupon);
    }

    /** Another store's coupon looks like it does not exist. */
    private Coupon findMyCoupon(Long storeId, Long couponId) {
        return couponRepository.findByIdAndStoreId(couponId, storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Coupon", couponId));
    }

    private void checkCodeIsFree(String code) {
        if (couponRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Coupon code " + code + " is already taken. Choose another code.");
        }
    }
}
