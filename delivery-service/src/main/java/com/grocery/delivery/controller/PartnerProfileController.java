package com.grocery.delivery.controller;

import com.grocery.delivery.context.UserContextHolder;
import com.grocery.delivery.dto.common.ApiResponse;
import com.grocery.delivery.dto.request.KycRequest;
import com.grocery.delivery.dto.response.AvailabilityResponse;
import com.grocery.delivery.dto.response.EarningsResponse;
import com.grocery.delivery.dto.response.PartnerResponse;
import com.grocery.delivery.service.PartnerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Delivery partner panel: MY profile, KYC, online status and earnings (the partner comes from X-User-Id).
 * Allowed role (checked by the API gateway): DELIVERY_PARTNER.
 */
@Tag(name = "Delivery partner - Profile", description = "Your profile, KYC, availability and earnings (DELIVERY_PARTNER)")
@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
public class PartnerProfileController {

    private final PartnerProfileService partnerProfileService;

    @Operation(summary = "My partner profile")
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<PartnerResponse>> getMyProfile() {
        return ResponseEntity.ok(ApiResponse.success("Profile fetched successfully",
                partnerProfileService.getMyProfile(UserContextHolder.getRequiredUserId())));
    }

    @Operation(summary = "Submit my KYC details (vehicle, driving licence, zone) for admin approval")
    @PutMapping("/profile/kyc")
    public ResponseEntity<ApiResponse<PartnerResponse>> submitKyc(@Valid @RequestBody KycRequest request) {
        return ResponseEntity.ok(ApiResponse.success("KYC submitted. An admin will review it.",
                partnerProfileService.submitKyc(UserContextHolder.getRequiredUserId(), request)));
    }

    @Operation(summary = "Am I online?")
    @GetMapping("/availability")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> getAvailability() {
        return ResponseEntity.ok(ApiResponse.success("Availability fetched",
                partnerProfileService.getAvailability(UserContextHolder.getRequiredUserId())));
    }

    @Operation(summary = "Go online (call again before onlineUntil to stay online); you may get a delivery right away")
    @PostMapping("/availability/online")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> goOnline() {
        return ResponseEntity.ok(ApiResponse.success("You are online",
                partnerProfileService.goOnline(UserContextHolder.getRequiredUserId())));
    }

    @Operation(summary = "Go offline (no new deliveries; the current one stays yours)")
    @PostMapping("/availability/offline")
    public ResponseEntity<ApiResponse<AvailabilityResponse>> goOffline() {
        return ResponseEntity.ok(ApiResponse.success("You are offline",
                partnerProfileService.goOffline(UserContextHolder.getRequiredUserId())));
    }

    @Operation(summary = "My earnings between two dates (both included), e.g. a day or a week")
    @GetMapping("/earnings")
    public ResponseEntity<ApiResponse<EarningsResponse>> getEarnings(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Earnings calculated",
                partnerProfileService.getEarnings(UserContextHolder.getRequiredUserId(), from, to)));
    }
}
