package com.grocery.platform.controller;

import com.grocery.platform.context.UserContextHolder;
import com.grocery.platform.dto.common.ApiResponse;
import com.grocery.platform.dto.common.PageResponse;
import com.grocery.platform.dto.request.FeatureFlagRequest;
import com.grocery.platform.dto.request.FeatureFlagSwitchRequest;
import com.grocery.platform.dto.request.SettingsRequest;
import com.grocery.platform.dto.response.AuditEntryResponse;
import com.grocery.platform.dto.response.FeatureFlagResponse;
import com.grocery.platform.dto.response.SettingsResponse;
import com.grocery.platform.service.AuditLogService;
import com.grocery.platform.service.FeatureFlagService;
import com.grocery.platform.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Super Admin panel: platform settings, feature flags and the audit log.
 * Allowed role (checked by the API gateway): SUPER_ADMIN.
 */
@Tag(name = "Super Admin - Platform", description = "Settings, feature flags, audit log (SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
public class SuperAdminPlatformController {

    private final SettingsService settingsService;
    private final FeatureFlagService featureFlagService;
    private final AuditLogService auditLogService;

    @Operation(summary = "The platform settings")
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsResponse>> getSettings() {
        return ResponseEntity.ok(ApiResponse.success("Settings fetched successfully", settingsService.getSettings()));
    }

    @Operation(summary = "Change the platform settings (cart-service uses the new delivery fee and minimum order a moment later)")
    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<SettingsResponse>> updateSettings(@Valid @RequestBody SettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Settings updated",
                settingsService.updateSettings(UserContextHolder.getRequiredUserId(), request)));
    }

    @Operation(summary = "All feature flags")
    @GetMapping("/feature-flags")
    public ResponseEntity<ApiResponse<List<FeatureFlagResponse>>> listFeatureFlags() {
        return ResponseEntity.ok(ApiResponse.success("Feature flags fetched successfully", featureFlagService.listFlags()));
    }

    @Operation(summary = "Add a feature flag")
    @PostMapping("/feature-flags")
    public ResponseEntity<ApiResponse<FeatureFlagResponse>> createFeatureFlag(@Valid @RequestBody FeatureFlagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Feature flag created",
                featureFlagService.createFlag(UserContextHolder.getRequiredUserId(), request)));
    }

    @Operation(summary = "Switch a feature on or off")
    @PatchMapping("/feature-flags/{key}")
    public ResponseEntity<ApiResponse<FeatureFlagResponse>> switchFeatureFlag(@PathVariable String key,
                                                                              @Valid @RequestBody FeatureFlagSwitchRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Feature flag " + (request.enabled() ? "enabled" : "disabled"),
                featureFlagService.switchFlag(UserContextHolder.getRequiredUserId(), key, request.enabled())));
    }

    @Operation(summary = "The audit log, newest first; every filter is optional (source = a topic or platform-service; from/to as ISO instants)")
    @GetMapping("/audit-log")
    public ResponseEntity<ApiResponse<PageResponse<AuditEntryResponse>>> searchAuditLog(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) String aggregateId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Audit log fetched successfully",
                auditLogService.search(eventType, source, userId, storeId, aggregateId, from, to, pageable)));
    }
}
