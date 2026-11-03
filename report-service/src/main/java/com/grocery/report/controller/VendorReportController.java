package com.grocery.report.controller;

import com.grocery.report.context.UserContextHolder;
import com.grocery.report.dto.response.DailySalesResponse;
import com.grocery.report.dto.response.TopProductResponse;
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
 * Store panel: the daily sales report of MY store (the store comes from X-Store-Id).
 * Allowed role (checked by the API gateway): VENDOR.
 */
@Tag(name = "Vendor - Reports", description = "Daily sales and best sellers of your store (VENDOR)")
@RestController
@RequestMapping("/api/v1/vendor/reports")
@RequiredArgsConstructor
public class VendorReportController {

    private final ReportQueryService reportQueryService;

    @Operation(summary = "My store's sales per day")
    @GetMapping("/sales/daily")
    public ResponseEntity<ApiResponse<DailySalesResponse>> getDailySales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Daily sales calculated",
                reportQueryService.getDailySales(from, to, UserContextHolder.getRequiredStoreId())));
    }

    @Operation(summary = "My best-selling products")
    @GetMapping("/top-products")
    public ResponseEntity<ApiResponse<List<TopProductResponse>>> getTopProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return ResponseEntity.ok(ApiResponse.success("Top products calculated",
                reportQueryService.getTopProducts(from, to, UserContextHolder.getRequiredStoreId(), limit)));
    }
}
