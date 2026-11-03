package com.grocery.platform.controller;

import com.grocery.platform.context.UserContextHolder;
import com.grocery.platform.dto.common.ApiResponse;
import com.grocery.platform.dto.request.BannerRequest;
import com.grocery.platform.dto.response.BannerResponse;
import com.grocery.platform.service.BannerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin panel: promotional banners.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Banners", description = "Promotional banners on the home page (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {

    private final BannerService bannerService;

    @Operation(summary = "All banners, including inactive and expired ones (see 'live')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerResponse>>> listBanners() {
        return ResponseEntity.ok(ApiResponse.success("Banners fetched successfully", bannerService.listAllBanners()));
    }

    @Operation(summary = "Add a banner")
    @PostMapping
    public ResponseEntity<ApiResponse<BannerResponse>> createBanner(@Valid @RequestBody BannerRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Banner created",
                bannerService.createBanner(UserContextHolder.getRequiredUserId(), request)));
    }

    @Operation(summary = "Change a banner")
    @PutMapping("/{bannerId}")
    public ResponseEntity<ApiResponse<BannerResponse>> updateBanner(@PathVariable Long bannerId, @Valid @RequestBody BannerRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Banner updated",
                bannerService.updateBanner(UserContextHolder.getRequiredUserId(), bannerId, request)));
    }

    @Operation(summary = "Remove a banner")
    @DeleteMapping("/{bannerId}")
    public ResponseEntity<ApiResponse<Void>> deleteBanner(@PathVariable Long bannerId) {
        bannerService.deleteBanner(UserContextHolder.getRequiredUserId(), bannerId);
        return ResponseEntity.ok(ApiResponse.success("Banner deleted", null));
    }
}
