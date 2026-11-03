package com.grocery.platform.service.impl;

import com.grocery.platform.dto.request.BannerRequest;
import com.grocery.platform.dto.response.BannerResponse;
import com.grocery.platform.entity.Banner;
import com.grocery.platform.exception.ResourceNotFoundException;
import com.grocery.platform.mapper.PlatformMapper;
import com.grocery.platform.repository.BannerRepository;
import com.grocery.platform.service.AuditLogService;
import com.grocery.platform.service.BannerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BannerServiceImpl implements BannerService {

    private final BannerRepository bannerRepository;
    private final PlatformMapper platformMapper;
    private final AuditLogService auditLogService;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public List<BannerResponse> listLiveBanners() {
        Instant now = Instant.now(clock);
        return bannerRepository.findByActiveTrueOrderByPositionAscIdAsc().stream()
                .filter(banner -> banner.isLiveAt(now))
                .map(banner -> platformMapper.toResponse(banner, now))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<BannerResponse> listAllBanners() {
        Instant now = Instant.now(clock);
        return bannerRepository.findAllByOrderByPositionAscIdAsc().stream().map(banner -> platformMapper.toResponse(banner, now)).toList();
    }

    @Override
    @Transactional
    public BannerResponse createBanner(Long adminUserId, BannerRequest request) {
        Banner banner = Banner.create();
        apply(banner, request);
        bannerRepository.save(banner);
        auditLogService.recordChange("BannerCreated", String.valueOf(banner.getId()), adminUserId, request.toString());
        return platformMapper.toResponse(banner, Instant.now(clock));
    }

    @Override
    @Transactional
    public BannerResponse updateBanner(Long adminUserId, Long bannerId, BannerRequest request) {
        Banner banner = findBanner(bannerId);
        apply(banner, request);
        auditLogService.recordChange("BannerUpdated", String.valueOf(bannerId), adminUserId, request.toString());
        return platformMapper.toResponse(banner, Instant.now(clock));
    }

    @Override
    @Transactional
    public void deleteBanner(Long adminUserId, Long bannerId) {
        Banner banner = findBanner(bannerId);
        bannerRepository.delete(banner);
        auditLogService.recordChange("BannerDeleted", String.valueOf(bannerId), adminUserId, "{\"title\": \"" + banner.getTitle() + "\"}");
    }

    private void apply(Banner banner, BannerRequest request) {
        banner.update(request.title().trim(), blankToNull(request.subtitle()), request.imageUrl().trim(), blankToNull(request.linkUrl()),
                request.position(), request.startsAt(), request.endsAt(), request.active());
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private Banner findBanner(Long bannerId) {
        return bannerRepository.findById(bannerId).orElseThrow(() -> new ResourceNotFoundException("Banner", bannerId));
    }
}
