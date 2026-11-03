package com.grocery.payment.repository;

import com.grocery.payment.TestcontainersConfiguration;
import com.grocery.payment.config.JpaAuditingConfig;
import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real MySQL (Testcontainers) + real Flyway migrations: the new column, the deadline query and the summary query.
 */
@DataJpaTest
@Import({TestcontainersConfiguration.class, JpaAuditingConfig.class})
@ActiveProfiles("test")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    @Test
    void findIdsWithDeadlineBefore_findsOnlyUnpaidOverduePayments() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Payment overdue = online(101L, "100.00", now.minusSeconds(60));
        Payment stillOpen = online(102L, "100.00", now.plusSeconds(600));
        Payment paid = online(103L, "100.00", now.minusSeconds(60));
        paid.markPaid("pay_103", now);
        paymentRepository.saveAllAndFlush(List.of(overdue, stillOpen, paid));

        assertThat(paymentRepository.findIdsWithDeadlineBefore(PaymentStatus.AWAITING_PAYMENT, now)).containsExactly(overdue.getId());
    }

    @Test
    void totalsCreatedBetween_groupsByMethodAndStatus() {
        Instant now = Instant.now();
        Payment paidA = online(201L, "100.00", now.plusSeconds(600));
        paidA.markPaid("pay_201", now);
        Payment paidB = online(202L, "50.50", now.plusSeconds(600));
        paidB.markPaid("pay_202", now);
        Payment cod = Payment.forOrder(203L, 4L, 1L, new BigDecimal("210.90"), PaymentMethod.COD);
        cod.acceptCashOnDelivery();
        cod.markCollected(now);
        paymentRepository.saveAllAndFlush(List.of(paidA, paidB, cod));

        var totals = paymentRepository.totalsCreatedBetween(now.minusSeconds(3600), now.plusSeconds(3600));

        assertThat(totals).contains(
                new PaymentTotals(PaymentMethod.COD, PaymentStatus.COLLECTED, 1L, new BigDecimal("210.90")),
                new PaymentTotals(PaymentMethod.ONLINE, PaymentStatus.PAID, 2L, new BigDecimal("150.50")));
    }

    private Payment online(Long orderId, String amount, Instant payBefore) {
        Payment payment = Payment.forOrder(orderId, 4L, 1L, new BigDecimal(amount), PaymentMethod.ONLINE);
        payment.awaitOnlinePayment("order_" + orderId, payBefore);
        return payment;
    }
}
