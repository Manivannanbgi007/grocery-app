package com.grocery.platform.controller;

import com.grocery.platform.dto.common.ApiResponse;
import com.grocery.platform.dto.response.BannerResponse;
import com.grocery.platform.dto.response.SettingsResponse;
import com.grocery.platform.service.BannerService;
import com.grocery.platform.service.FeatureFlagService;
import com.grocery.platform.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * What every app screen needs, without logging in: settings, feature switches and the home page banners.
 */
@Tag(name = "Platform", description = "Public settings, feature flags and banners")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class PublicPlatformController {

    private final SettingsService settingsService;
    private final FeatureFlagService featureFlagService;
    private final BannerService bannerService;

    @Operation(summary = "Currency, delivery fee, free delivery amount, minimum order value, support contact")
    @GetMapping("/platform/settings")
    public ResponseEntity<ApiResponse<SettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success("Settings fetched successfully", settingsService.getSettings()));
    }

    @Operation(summary = "Feature switches, e.g. {\"DELIVERY_SLOTS\": false}")
    @GetMapping("/platform/feature-flags")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> getFeatureFlags() {
        return ResponseEntity.ok(ApiResponse.success("Feature flags fetched successfully", featureFlagService.getFlagStates()));
    }

    @Operation(summary = "The banners to show on the home page right now")
    @GetMapping("/banners")
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getBanners() {
        return ResponseEntity.ok(ApiResponse.success("Banners fetched successfully", bannerService.listLiveBanners()));
    }
}
