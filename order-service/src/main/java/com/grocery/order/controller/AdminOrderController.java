package com.grocery.order.controller;

import com.grocery.order.dto.common.ApiResponse;
import com.grocery.order.dto.common.PageResponse;
import com.grocery.order.dto.request.ReasonRequest;
import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.dto.response.OrderResponse;
import com.grocery.order.dto.response.OrderSummaryResponse;
import com.grocery.order.enums.OrderStatus;
import com.grocery.order.service.AdminOrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin panel: monitor all orders and resolve problems.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Orders", description = "Monitor all orders and cancel problem orders (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/orders")
@RequiredArgsConstructor
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    @Operation(summary = "Search all orders by customer, store and status")
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<OrderSummaryResponse>>> searchOrders(
            @RequestParam(required = false) Long customerUserId,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) OrderStatus status,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable) {
        PageResponse<OrderSummaryResponse> orders = adminOrderService.searchOrders(customerUserId, storeId, status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Orders fetched successfully", orders));
    }

    @Operation(summary = "Any order")
    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success("Order fetched successfully", adminOrderService.getOrder(orderId)));
    }

    @Operation(summary = "The tax invoice as a PDF (once the store accepted the order)")
    @GetMapping(value = "/{orderId}/invoice", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getInvoice(@PathVariable Long orderId) {
        return InvoiceFile.asDownload(adminOrderService.getInvoice(orderId));
    }

    @Operation(summary = "Cancel a problem order (before it leaves the store)")
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable Long orderId, @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Order cancelled", adminOrderService.cancelOrder(orderId, request.reason())));
    }
}
