package com.grocery.report.service.impl;

import com.grocery.report.config.ReportProperties;
import com.grocery.report.dto.response.CommissionReportResponse;
import com.grocery.report.dto.response.DailySalesResponse;
import com.grocery.report.dto.response.DashboardResponse;
import com.grocery.report.dto.response.TopProductResponse;
import com.grocery.report.dto.response.TopStoreResponse;
import com.grocery.report.entity.OrderFact;
import com.grocery.report.entity.StoreFact;
import com.grocery.report.enums.ReportOrderStatus;
import com.grocery.report.exception.BusinessException;
import com.grocery.report.repository.DailyOrderCountRow;
import com.grocery.report.repository.DailySalesRow;
import com.grocery.report.repository.OrderFactRepository;
import com.grocery.report.repository.OrderItemFactRepository;
import com.grocery.report.repository.PartnerFactRepository;
import com.grocery.report.repository.StockFactRepository;
import com.grocery.report.repository.StoreFactRepository;
import com.grocery.report.repository.UserFactRepository;
import com.grocery.report.service.ReportQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportQueryServiceImpl implements ReportQueryService {

    private static final int MAX_DAYS = 366;
    private static final Duration RECENT = Duration.ofDays(30);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final OrderFactRepository orderFactRepository;
    private final OrderItemFactRepository orderItemFactRepository;
    private final StoreFactRepository storeFactRepository;
    private final UserFactRepository userFactRepository;
    private final StockFactRepository stockFactRepository;
    private final PartnerFactRepository partnerFactRepository;
    private final ReportProperties reportProperties;
    private final Clock clock;

    @Override
    public DashboardResponse getDashboard() {
        Instant now = Instant.now(clock);
        LocalDate today = now.atZone(reportProperties.timeZone()).toLocalDate();
        Instant since = now.minus(RECENT);
        Double averageMinutes = orderFactRepository.averageDeliveryMinutesSince(since);
        return new DashboardResponse(
                today,
                orderFactRepository.count(),
                orderFactRepository.countByPlacedDate(today),
                scaled(orderFactRepository.salesPlacedOn(today)),
                scaled(orderFactRepository.totalOfStatusDeliveredOn(ReportOrderStatus.DELIVERED, today)),
                storeFactRepository.countByStatus("ACTIVE"),
                partnerFactRepository.countByKycStatus("APPROVED"),
                userFactRepository.countByRole("CUSTOMER"),
                orderFactRepository.countCustomersWhoOrderedSince(since),
                orderFactRepository.countPendingRefunds(),
                stockFactRepository.countByAvailableQuantity(0),
                averageMinutes == null ? null : (int) Math.round(averageMinutes),
                now);
    }

    @Override
    public DailySalesResponse getDailySales(LocalDate from, LocalDate to, Long storeId) {
        checkPeriod(from, to);
        Map<LocalDate, DailyOrderCountRow> counts = orderFactRepository.dailyOrderCounts(from, to, storeId).stream()
                .collect(Collectors.toMap(DailyOrderCountRow::date, Function.identity()));
        Map<LocalDate, DailySalesRow> sales = orderFactRepository.dailySales(from, to, storeId).stream()
                .collect(Collectors.toMap(DailySalesRow::date, Function.identity()));

        List<DailySalesResponse.Day> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DailyOrderCountRow count = counts.get(date);
            DailySalesRow sale = sales.get(date);
            days.add(new DailySalesResponse.Day(date,
                    count == null ? 0 : count.orders(),
                    count == null ? 0 : count.cancelledOrders(),
                    sale == null ? 0 : sale.salesOrders(),
                    sale == null ? ZERO : scaled(sale.itemsTotal()),
                    sale == null ? ZERO : scaled(sale.couponDiscounts()),
                    sale == null ? ZERO : scaled(sale.deliveryFees()),
                    sale == null ? ZERO : scaled(sale.sales())));
        }
        BigDecimal totalSales = days.stream().map(DailySalesResponse.Day::sales).reduce(ZERO, BigDecimal::add);
        long totalOrders = days.stream().mapToLong(DailySalesResponse.Day::orders).sum();
        return new DailySalesResponse(from, to, storeId, days, totalSales, totalOrders);
    }

    @Override
    public List<TopStoreResponse> getTopStores(LocalDate from, LocalDate to, int limit) {
        checkPeriod(from, to);
        Map<Long, String> names = storeNames();
        return orderFactRepository.topStores(from, to, PageRequest.of(0, limit)).stream()
                .map(row -> new TopStoreResponse(row.storeId(), names.getOrDefault(row.storeId(), "Store " + row.storeId()),
                        row.orders(), scaled(row.sales())))
                .toList();
    }

    @Override
    public List<TopProductResponse> getTopProducts(LocalDate from, LocalDate to, Long storeId, int limit) {
        checkPeriod(from, to);
        return orderItemFactRepository.topProducts(from, to, storeId, PageRequest.of(0, limit)).stream()
                .map(row -> new TopProductResponse(row.productId(), row.productName(), row.quantity(), scaled(row.sales())))
                .toList();
    }

    /** Calculated in Java (not SQL): every order has its own commission rate, which is easy to read this way. */
    @Override
    public CommissionReportResponse getCommissionReport(LocalDate from, LocalDate to) {
        checkPeriod(from, to);
        Map<Long, String> names = storeNames();
        Map<Long, List<OrderFact>> ordersByStore = orderFactRepository
                .findByStatusAndDeliveredDateBetweenOrderByStoreIdAsc(ReportOrderStatus.DELIVERED, from, to).stream()
                .collect(Collectors.groupingBy(OrderFact::getStoreId, LinkedHashMap::new, Collectors.toList()));

        List<CommissionReportResponse.StoreCommission> stores = new ArrayList<>();
        ordersByStore.forEach((storeId, orders) -> {
            BigDecimal itemsTotal = orders.stream().map(OrderFact::getItemsTotal).reduce(ZERO, BigDecimal::add);
            BigDecimal commission = orders.stream().map(this::commissionOf).reduce(ZERO, BigDecimal::add);
            stores.add(new CommissionReportResponse.StoreCommission(storeId, names.getOrDefault(storeId, "Store " + storeId),
                    orders.size(), itemsTotal, commission, itemsTotal.subtract(commission)));
        });
        BigDecimal totalItems = stores.stream().map(CommissionReportResponse.StoreCommission::itemsTotal).reduce(ZERO, BigDecimal::add);
        BigDecimal totalCommission = stores.stream().map(CommissionReportResponse.StoreCommission::commission).reduce(ZERO, BigDecimal::add);
        return new CommissionReportResponse(from, to, stores, totalItems, totalCommission, totalItems.subtract(totalCommission));
    }

    /** 120.00 at 10 % -> 12.00 (orders placed before commission snapshots count 0). */
    private BigDecimal commissionOf(OrderFact order) {
        BigDecimal percentage = order.getCommissionPercentage() == null ? BigDecimal.ZERO : order.getCommissionPercentage();
        return order.getItemsTotal().multiply(percentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    private Map<Long, String> storeNames() {
        return storeFactRepository.findAll().stream().collect(Collectors.toMap(StoreFact::getStoreId, StoreFact::getName));
    }

    private void checkPeriod(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new BusinessException("'from' must not be after 'to'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new BusinessException("A report covers at most " + MAX_DAYS + " days");
        }
    }

    private BigDecimal scaled(BigDecimal amount) {
        return amount == null ? ZERO : amount.setScale(2, RoundingMode.HALF_UP);
    }
}
