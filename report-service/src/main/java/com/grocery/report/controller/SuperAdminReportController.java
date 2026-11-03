package com.grocery.report.controller;

import com.grocery.report.dto.response.CommissionReportResponse;
import com.grocery.report.service.ReportQueryService;
import com.grocery.report.dto.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Super Admin panel: financial reports.
 * Allowed role (checked by the API gateway): SUPER_ADMIN.
 */
@Tag(name = "Super Admin - Reports", description = "Commission per store (SUPER_ADMIN)")
@RestController
@RequestMapping("/api/v1/super-admin/reports")
@RequiredArgsConstructor
public class SuperAdminReportController {

    private final ReportQueryService reportQueryService;

    @Operation(summary = "Platform commission and store payout per store, on the orders delivered in the period")
    @GetMapping("/commissions")
    public ResponseEntity<ApiResponse<CommissionReportResponse>> getCommissionReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(ApiResponse.success("Commission report calculated", reportQueryService.getCommissionReport(from, to)));
    }
}
