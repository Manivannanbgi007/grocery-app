package com.grocery.platform.service;

import com.grocery.platform.dto.request.BannerRequest;
import com.grocery.platform.dto.response.BannerResponse;

import java.util.List;

public interface BannerService {

    /** Public: the banners customers see right now, in page order. */
    List<BannerResponse> listLiveBanners();

    List<BannerResponse> listAllBanners();

    BannerResponse createBanner(Long adminUserId, BannerRequest request);

    BannerResponse updateBanner(Long adminUserId, Long bannerId, BannerRequest request);

    void deleteBanner(Long adminUserId, Long bannerId);
}
