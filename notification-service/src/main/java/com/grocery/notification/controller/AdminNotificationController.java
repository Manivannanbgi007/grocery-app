package com.grocery.notification.controller;

import com.grocery.notification.dto.common.ApiResponse;
import com.grocery.notification.dto.common.PageResponse;
import com.grocery.notification.dto.response.NotificationResponse;
import com.grocery.notification.enums.EmailStatus;
import com.grocery.notification.enums.NotificationType;
import com.grocery.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin panel: what the platform sent to whom, and which emails failed.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Notifications", description = "All notifications and email results (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/notifications")
@RequiredArgsConstructor
public class AdminNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "Search notifications, e.g. emailStatus=FAILED")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> searchNotifications(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) NotificationType type,
            @RequestParam(required = false) EmailStatus emailStatus,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched successfully",
                notificationService.searchNotifications(userId, type, emailStatus, pageable)));
    }
}
