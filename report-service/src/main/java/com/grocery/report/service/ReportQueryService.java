package com.grocery.report.service;

import com.grocery.report.dto.response.CommissionReportResponse;
import com.grocery.report.dto.response.DailySalesResponse;
import com.grocery.report.dto.response.DashboardResponse;
import com.grocery.report.dto.response.TopProductResponse;
import com.grocery.report.dto.response.TopStoreResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Reads the read model. Every period is "from" to "to", both days included, at most 366 days.
 */
public interface ReportQueryService {

    DashboardResponse getDashboard();

    /** storeId = null for all stores. */
    DailySalesResponse getDailySales(LocalDate from, LocalDate to, Long storeId);

    List<TopStoreResponse> getTopStores(LocalDate from, LocalDate to, int limit);

    /** storeId = null for all stores. */
    List<TopProductResponse> getTopProducts(LocalDate from, LocalDate to, Long storeId, int limit);

    CommissionReportResponse getCommissionReport(LocalDate from, LocalDate to);
}
