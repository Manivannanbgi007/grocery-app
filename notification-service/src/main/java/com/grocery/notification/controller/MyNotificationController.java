package com.grocery.notification.controller;

import com.grocery.notification.context.UserContextHolder;
import com.grocery.notification.dto.common.ApiResponse;
import com.grocery.notification.dto.common.PageResponse;
import com.grocery.notification.dto.response.CountResponse;
import com.grocery.notification.dto.response.NotificationResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Every panel: MY notifications (the bell icon). The user comes from X-User-Id.
 * Allowed roles (checked by the API gateway): any logged-in user.
 */
@Tag(name = "Notifications", description = "Your in-app notifications (any logged-in user)")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class MyNotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "My notifications, newest first (unreadOnly=true for the unread ones)")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> listMyNotifications(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Notifications fetched successfully",
                notificationService.listMyNotifications(UserContextHolder.getRequiredUserId(), unreadOnly, pageable)));
    }

    @Operation(summary = "How many unread notifications I have (the badge)")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<CountResponse>> countUnread() {
        return ResponseEntity.ok(ApiResponse.success("Unread notifications counted",
                notificationService.countUnread(UserContextHolder.getRequiredUserId())));
    }

    @Operation(summary = "Mark one notification as read")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<NotificationResponse>> markRead(@PathVariable Long notificationId) {
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read",
                notificationService.markRead(UserContextHolder.getRequiredUserId(), notificationId)));
    }

    @Operation(summary = "Mark all my notifications as read")
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<CountResponse>> markAllRead() {
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read",
                notificationService.markAllRead(UserContextHolder.getRequiredUserId())));
    }
}
