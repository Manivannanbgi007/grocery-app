package com.grocery.report.entity;

import com.grocery.report.enums.ReportOrderStatus;
import com.grocery.report.enums.ReportPaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFactTest {

    private static final Instant NOW = Instant.parse("2026-10-26T06:00:00Z");
    private static final LocalDate TODAY = LocalDate.parse("2026-10-26");

    @Test
    void aConfirmedOrderIsASale_untilItEndsWithoutDelivery() {
        OrderFact order = order("ONLINE");
        assertThat(order.isSale()).isFalse();   // PENDING: stock and payment not settled yet

        order.paymentReceived("ONLINE");
        order.confirm();
        assertThat(order.isSale()).isTrue();

        order.end(ReportOrderStatus.REJECTED, NOW);
        assertThat(order.isSale()).isFalse();
        assertThat(order.isCancelled()).isTrue();
        assertThat(order.getPaymentStatus()).isEqualTo(ReportPaymentStatus.PAID);   // refund still pending

        order.refunded();
        assertThat(order.getPaymentStatus()).isEqualTo(ReportPaymentStatus.REFUNDED);
    }

    @Test
    void codIsPaidWhenDelivered() {
        OrderFact order = order("COD");
        order.paymentReceived("COD");
        order.confirm();
        assertThat(order.getPaymentStatus()).isEqualTo(ReportPaymentStatus.CASH_ON_DELIVERY);

        order.deliver(NOW, TODAY);

        assertThat(order.getStatus()).isEqualTo(ReportOrderStatus.DELIVERED);
        assertThat(order.getPaymentStatus()).isEqualTo(ReportPaymentStatus.PAID);
        assertThat(order.getDeliveredDate()).isEqualTo(TODAY);
    }

    @Test
    void stockChanges_olderThanTheStoredOne_areIgnored() {
        StockFact stock = new StockFact(1L);
        assertThat(stock.apply(1L, 10, 5)).isTrue();
        assertThat(stock.apply(1L, 99, 4)).isFalse();
        assertThat(stock.getAvailableQuantity()).isEqualTo(10);
    }

    private OrderFact order(String method) {
        return OrderFact.placed(1L, 4L, 1L, method, new BigDecimal("120.00"), BigDecimal.ZERO, new BigDecimal("30.00"),
                new BigDecimal("150.00"), new BigDecimal("10.00"), NOW, TODAY);
    }
}
