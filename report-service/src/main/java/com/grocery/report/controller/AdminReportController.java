package com.grocery.report.controller;

import com.grocery.report.dto.response.DailySalesResponse;
import com.grocery.report.dto.response.DashboardResponse;
import com.grocery.report.dto.response.TopProductResponse;
import com.grocery.report.dto.response.TopStoreResponse;
import com.grocery.report.service.ReportQueryService;
import com.grocery.report.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin panel: dashboard and reports over all stores. Dates are India dates (yyyy-MM-dd), both days included.
 * Allowed roles (checked by the API gateway): ADMIN, SUPER_ADMIN.
 */
@Tag(name = "Admin - Reports", description = "Dashboard, sales, top stores and products (ADMIN, SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportQueryService reportQueryService;

    @Operation(summary = "Dashboard: orders, sales, revenue, active stores and partners, refunds, out-of-stock, delivery time")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<DashboardResponse>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.success("Dashboard calculated", reportQueryService.getDashboard()));
    }

    @Operation(summary = "Sales per day; storeId is optional")
    @GetMapping("/sales/daily")
    public ResponseEntity<ApiResponse<DailySalesResponse>> getDailySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(ApiResponse.success("Daily sales calculated", reportQueryService.getDailySales(from, to, storeId)));
    }

    @Operation(summary = "The stores with the highest sales")
    @GetMapping("/top-stores")
    public ResponseEntity<ApiResponse<List<TopStoreResponse>>> getTopStores(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(ApiResponse.success("Top stores calculated", reportQueryService.getTopStores(from, to, limit)));
    }

    @Operation(summary = "The best-selling products (by quantity); storeId is optional")
    @GetMapping("/top-products")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> getTopProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(ApiResponse.success("Top products calculated", reportQueryService.getTopProducts(from, to, storeId, limit)));
    }
}
