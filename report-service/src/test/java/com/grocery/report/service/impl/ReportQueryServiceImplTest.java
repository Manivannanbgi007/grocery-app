package com.grocery.report.service.impl;

import com.grocery.report.config.ReportProperties;
import com.grocery.report.dto.response.DailySalesResponse;
import com.grocery.report.exception.BusinessException;
import com.grocery.report.repository.DailyOrderCountRow;
import com.grocery.report.repository.DailySalesRow;
import com.grocery.report.repository.OrderFactRepository;
import com.grocery.report.repository.OrderItemFactRepository;
import com.grocery.report.repository.PartnerFactRepository;
import com.grocery.report.repository.StockFactRepository;
import com.grocery.report.repository.StoreFactRepository;
import com.grocery.report.repository.UserFactRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The periods of the sales reports (the admin dashboard's tabs, "Pick a day" and "Custom range"): one day is a period
 * whose first and last day are the same; days without orders come back as zeros; a period runs forwards and covers at
 * most 366 days.
 */
class ReportQueryServiceImplTest {

    private static final LocalDate DAY = LocalDate.parse("2026-10-20");

    private final OrderFactRepository orderFactRepository = mock(OrderFactRepository.class);
    private final ReportQueryServiceImpl service = new ReportQueryServiceImpl(
            orderFactRepository,
            mock(OrderItemFactRepository.class),
            mock(StoreFactRepository.class),
            mock(UserFactRepository.class),
            mock(StockFactRepository.class),
            mock(PartnerFactRepository.class),
            new ReportProperties(ZoneId.of("Asia/Kolkata")),
            Clock.fixed(Instant.parse("2026-10-27T06:30:00Z"), ZoneId.of("Asia/Kolkata")));

    @Test
    void oneDayIsAPeriodWhoseFirstAndLastDayAreTheSame() {
        when(orderFactRepository.dailyOrderCounts(DAY, DAY, null)).thenReturn(List.of(new DailyOrderCountRow(DAY, 3L, 1L)));
        when(orderFactRepository.dailySales(DAY, DAY, null)).thenReturn(List.of(
                new DailySalesRow(DAY, 2L, new BigDecimal("400"), new BigDecimal("20"), new BigDecimal("30"), new BigDecimal("410"))));

        DailySalesResponse sales = service.getDailySales(DAY, DAY, null);

        assertThat(sales.days()).hasSize(1);
        DailySalesResponse.Day day = sales.days().getFirst();
        assertThat(day.date()).isEqualTo(DAY);
        assertThat(day.orders()).isEqualTo(3);
        assertThat(day.cancelledOrders()).isEqualTo(1);
        assertThat(day.salesOrders()).isEqualTo(2);
        assertThat(sales.totalSales()).isEqualByComparingTo("410.00");
        assertThat(sales.totalOrders()).isEqualTo(3);
    }

    @Test
    void everyDayOfARangeIsListed_daysWithoutOrdersAsZeros() {
        LocalDate from = DAY.minusDays(2);
        when(orderFactRepository.dailyOrderCounts(from, DAY, 1L)).thenReturn(List.of(new DailyOrderCountRow(DAY.minusDays(1), 2L, 0L)));
        when(orderFactRepository.dailySales(from, DAY, 1L)).thenReturn(List.of(
                new DailySalesRow(DAY.minusDays(1), 2L, new BigDecimal("250"), BigDecimal.ZERO, new BigDecimal("30"), new BigDecimal("280"))));

        DailySalesResponse sales = service.getDailySales(from, DAY, 1L);

        assertThat(sales.days()).extracting(DailySalesResponse.Day::date).containsExactly(from, DAY.minusDays(1), DAY);
        assertThat(sales.days()).extracting(DailySalesResponse.Day::orders).containsExactly(0L, 2L, 0L);
        assertThat(sales.days().getFirst().sales()).isEqualByComparingTo("0");
        assertThat(sales.totalSales()).isEqualByComparingTo("280.00");
        assertThat(sales.totalOrders()).isEqualTo(2);
    }

    @Test
    void noOrdersAtAllGivesZeroTotals() {
        DailySalesResponse sales = service.getDailySales(DAY, DAY, null);

        assertThat(sales.days()).singleElement().satisfies(day -> assertThat(day.orders()).isZero());
        assertThat(sales.totalSales()).isEqualByComparingTo("0");
        assertThat(sales.totalOrders()).isZero();
    }

    @Test
    void aFirstDayAfterTheLastDayIsRejected() {
        assertThatThrownBy(() -> service.getDailySales(DAY, DAY.minusDays(1), null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("'from' must not be after 'to'");
        assertThatThrownBy(() -> service.getTopStores(DAY, DAY.minusDays(1), 5)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.getTopProducts(DAY, DAY.minusDays(1), null, 5)).isInstanceOf(BusinessException.class);
    }

    @Test
    void aPeriodCoversAtMost366Days() {
        assertThat(service.getDailySales(DAY.minusDays(365), DAY, null).days()).hasSize(366);

        assertThatThrownBy(() -> service.getDailySales(DAY.minusDays(366), DAY, null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A report covers at most 366 days");
    }
}
