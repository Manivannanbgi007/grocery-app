package com.grocery.order.invoice;

import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.exception.BusinessException;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static com.grocery.order.support.TestOrders.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoicePdfGeneratorTest {

    private final InvoicePdfGenerator generator = new InvoicePdfGenerator();

    @Test
    void anAcceptedOrder_getsAPdfInvoice_withTheTaxOfEveryLine() throws Exception {
        Order order = pendingOrder(PaymentMethod.COD);
        order.setId(123L);
        order.setCreatedAt(Instant.parse("2026-10-26T06:30:00Z"));
        order.confirm(OrderPaymentStatus.CASH_ON_DELIVERY);
        order.accept();

        InvoiceFile invoice = generator.generate(order);

        assertThat(invoice.fileName()).isEqualTo("invoice-INV-000123.pdf");
        assertThat(new String(invoice.content(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        String text = new PdfTextExtractor(new PdfReader(invoice.content())).getTextFromPage(1);
        assertThat(text).contains("TAX INVOICE", "INV-000123", "Fresh Mart", "Priya Sharma", "Amul Butter", "158.00",
                "Payment: cash on delivery, to be paid on delivery");
    }

    @Test
    void aPendingOrder_hasNoInvoiceYet() {
        Order order = pendingOrder(PaymentMethod.ONLINE);
        order.setId(124L);

        assertThatThrownBy(() -> generator.generate(order))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has no invoice (it is PENDING)");
    }

    @Test
    void thePaymentLine_namesHowAndWhetherItWasPaid_andThePayment() {
        Order online = pendingOrder(PaymentMethod.ONLINE);
        online.confirm(OrderPaymentStatus.PAID, 42L);
        Order cashNotCollected = pendingOrder(PaymentMethod.COD);
        cashNotCollected.confirm(OrderPaymentStatus.CASH_ON_DELIVERY, 43L);
        Order cashCollected = pendingOrder(PaymentMethod.COD);
        cashCollected.confirm(OrderPaymentStatus.PAID, 44L);
        Order olderOrder = pendingOrder(PaymentMethod.ONLINE);
        olderOrder.confirm(OrderPaymentStatus.PAID);   // before payment ids were kept

        assertThat(InvoicePdfGenerator.paymentLine(online)).isEqualTo("Payment: paid online (payment #42)");
        assertThat(InvoicePdfGenerator.paymentLine(cashNotCollected))
                .isEqualTo("Payment: cash on delivery, to be paid on delivery (payment #43)");
        assertThat(InvoicePdfGenerator.paymentLine(cashCollected)).isEqualTo("Payment: cash on delivery, paid (payment #44)");
        assertThat(InvoicePdfGenerator.paymentLine(olderOrder)).isEqualTo("Payment: paid online");
    }

    @Test
    void taxIncludedInAPrice() {
        assertThat(InvoicePdfGenerator.taxIncludedIn(new BigDecimal("56.00"), new BigDecimal("12.00"))).isEqualByComparingTo("6.00");
        assertThat(InvoicePdfGenerator.taxIncludedIn(new BigDecimal("72.00"), BigDecimal.ZERO)).isEqualByComparingTo("0.00");
    }
}
