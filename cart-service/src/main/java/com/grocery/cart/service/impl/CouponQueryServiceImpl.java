package com.grocery.cart.service.impl;

import com.grocery.cart.dto.response.PublicCouponResponse;
import com.grocery.cart.mapper.CouponMapper;
import com.grocery.cart.repository.CouponRepository;
import com.grocery.cart.service.CouponQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CouponQueryServiceImpl implements CouponQueryService {

    private final CouponRepository couponRepository;
    private final CouponMapper couponMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<PublicCouponResponse> listAvailableCoupons(Long storeId) {
        return couponRepository.findAvailable(storeId, Instant.now(clock), Sort.by("code")).stream()
                .map(couponMapper::toPublicResponse)
                .toList();
    }
}
