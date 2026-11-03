package com.grocery.order;

import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.entity.Invoice;
import com.grocery.order.entity.Order;
import com.grocery.order.enums.ChangedBy;
import com.grocery.order.enums.OrderPaymentStatus;
import com.grocery.order.enums.PaymentMethod;
import com.grocery.order.exception.BusinessException;
import com.grocery.order.exception.InvoiceUnavailableException;
import com.grocery.order.invoice.InvoicePdfGenerator;
import com.grocery.order.invoice.InvoiceStorage;
import com.grocery.order.repository.InvoiceRepository;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.service.InvoiceService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.grocery.order.support.TestOrders.pendingOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * InvoiceServiceImpl with a real MySQL and a real folder: generated once, stored, reused, repaired, and what happens
 * when generating or storing fails. Kafka is not needed here, so its listeners are not started.
 * The spies count how often a PDF is really generated.
 */
@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class InvoiceServiceIntegrationTest {

    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    static Path folder;

    @DynamicPropertySource
    static void invoiceFolder(DynamicPropertyRegistry registry) {
        registry.add("app.invoice.storage-dir", () -> folder.toString());
    }

    @Autowired
    private InvoiceService invoiceService;
    @Autowired
    private OrderRepository orderRepository;
    @Autowired
    private InvoiceRepository invoiceRepository;
    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private InvoicePdfGenerator invoicePdfGenerator;
    @MockitoSpyBean
    private InvoiceStorage invoiceStorage;

    @Test
    void theFirstRequest_generatesAndStores_laterRequestsReadTheStoredFile() {
        Long orderId = paidOnlineOrder();

        InvoiceFile first = invoiceService.getInvoice(order(orderId));
        InvoiceFile second = invoiceService.getInvoice(order(orderId));

        verify(invoicePdfGenerator, times(1)).generate(any());
        assertThat(second.content()).isEqualTo(first.content());
        assertThat(second.fileName()).isEqualTo("invoice-INV-%06d.pdf".formatted(orderId));
        Invoice invoice = invoiceRepository.findByOrderId(orderId).orElseThrow();
        assertThat(invoice.getInvoiceNumber()).isEqualTo("INV-%06d".formatted(orderId));
        assertThat(invoice.getCustomerUserId()).isEqualTo(4L);
        assertThat(invoice.getStoreId()).isEqualTo(1L);
        assertThat(invoice.getPaymentId()).isEqualTo(42L);
        assertThat(folder.resolve(invoice.getFilePath())).hasBinaryContent(first.content());
    }

    @Test
    void parallelFirstRequests_generateTheInvoiceOnlyOnce() throws Exception {
        Long orderId = paidOnlineOrder();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<InvoiceFile>> requests = new ArrayList<>();

        try (ExecutorService pool = Executors.newFixedThreadPool(5)) {
            for (int i = 0; i < 5; i++) {
                requests.add(pool.submit(() -> {
                    start.await();   // all five start at the same moment
                    return invoiceService.getInvoice(order(orderId));
                }));
            }
            start.countDown();
            List<byte[]> pdfs = new ArrayList<>();
            for (Future<InvoiceFile> request : requests) {
                pdfs.add(request.get(60, TimeUnit.SECONDS).content());
            }
            assertThat(pdfs).allSatisfy(pdf -> assertThat(pdf).isEqualTo(pdfs.getFirst()));
        }

        verify(invoicePdfGenerator, times(1)).generate(any());
        assertThat(invoiceRepository.findAll()).filteredOn(invoice -> invoice.getOrderId().equals(orderId)).hasSize(1);
    }

    @Test
    void aDeletedFile_isGeneratedAndStoredAgain() throws IOException {
        Long orderId = paidOnlineOrder();
        invoiceService.getInvoice(order(orderId));
        Path file = folder.resolve(invoiceRepository.findByOrderId(orderId).orElseThrow().getFilePath());
        Files.delete(file);

        InvoiceFile again = invoiceService.getInvoice(order(orderId));
        invoiceService.getInvoice(order(orderId));   // the record matches the new file: read, not generated

        verify(invoicePdfGenerator, times(2)).generate(any());
        assertThat(file).hasBinaryContent(again.content());
    }

    @Test
    void aDamagedFile_isGeneratedAndStoredAgain() throws IOException {
        Long orderId = paidOnlineOrder();
        invoiceService.getInvoice(order(orderId));
        Path file = folder.resolve(invoiceRepository.findByOrderId(orderId).orElseThrow().getFilePath());
        byte[] damaged = Files.readAllBytes(file);
        damaged[damaged.length / 2] ^= 1;   // one bit changed, same size: only the SHA-256 notices
        Files.write(file, damaged);

        InvoiceFile repaired = invoiceService.getInvoice(order(orderId));

        verify(invoicePdfGenerator, times(2)).generate(any());
        assertThat(repaired.content()).startsWith(PDF_HEADER).isNotEqualTo(damaged);
        assertThat(file).hasBinaryContent(repaired.content());
    }

    @Test
    void cashOnDelivery_isNotStoredBeforeTheCashIsCollected_andStoredAfterDelivery() throws IOException {
        Order order = pendingOrder(PaymentMethod.COD);
        order.confirm(OrderPaymentStatus.CASH_ON_DELIVERY, 43L);
        order.accept();
        Long orderId = orderRepository.save(order).getId();

        invoiceService.getInvoice(order(orderId));
        invoiceService.getInvoice(order(orderId));
        verify(invoicePdfGenerator, times(2)).generate(any());
        assertThat(invoiceRepository.findByOrderId(orderId)).isEmpty();

        deliver(orderId);
        InvoiceFile paid = invoiceService.getInvoice(order(orderId));

        assertThat(invoiceRepository.findByOrderId(orderId)).isPresent();
        assertThat(new PdfTextExtractor(new PdfReader(paid.content())).getTextFromPage(1))
                .contains("Payment: cash on delivery, paid (payment #43)");
    }

    @Test
    void whenGeneratingFails_theAnswerAsksToTryAgain_nothingIsStored_andARetryWorks() {
        Long orderId = paidOnlineOrder();
        doThrow(new IllegalStateException("PDF library failed")).when(invoicePdfGenerator).generate(any());

        assertThatThrownBy(() -> invoiceService.getInvoice(order(orderId)))
                .isInstanceOf(InvoiceUnavailableException.class)
                .hasMessage("The invoice could not be created right now. Please try again in a moment.");
        assertThat(invoiceRepository.findByOrderId(orderId)).isEmpty();

        reset(invoicePdfGenerator);   // works again
        invoiceService.getInvoice(order(orderId));
        assertThat(invoiceRepository.findByOrderId(orderId)).isPresent();
    }

    @Test
    void whenStoringFails_theCustomerStillGetsThePdf_andTheNextRequestStoresIt() throws IOException {
        Long orderId = paidOnlineOrder();
        doThrow(new IOException("No space left on device")).when(invoiceStorage).save(any(), any(), any());

        InvoiceFile pdf = invoiceService.getInvoice(order(orderId));

        assertThat(pdf.content()).startsWith(PDF_HEADER);
        assertThat(invoiceRepository.findByOrderId(orderId)).isEmpty();

        reset(invoiceStorage);   // the disk has space again
        invoiceService.getInvoice(order(orderId));
        assertThat(invoiceRepository.findByOrderId(orderId)).isPresent();
    }

    @Test
    void anOrderTheStoreHasNotAcceptedYet_hasNoInvoice() {
        Order order = pendingOrder(PaymentMethod.ONLINE);
        order.confirm(OrderPaymentStatus.PAID, 45L);
        Long orderId = orderRepository.save(order).getId();

        assertThatThrownBy(() -> invoiceService.getInvoice(order(orderId)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("has no invoice (it is CONFIRMED)");
        verify(invoicePdfGenerator, never()).generate(any());
    }

    /** Paid online (payment 42) and accepted by the store. */
    private Long paidOnlineOrder() {
        Order order = pendingOrder(PaymentMethod.ONLINE);
        order.confirm(OrderPaymentStatus.PAID, 42L);
        order.accept();
        return orderRepository.save(order).getId();
    }

    /** What the three services pass in: load the order (their access check is not needed here). */
    private Supplier<Order> order(Long orderId) {
        return () -> orderRepository.findById(orderId).orElseThrow();
    }

    private void deliver(Long orderId) {
        transactionTemplate.executeWithoutResult(status -> {
            Order order = orderRepository.findById(orderId).orElseThrow();
            order.pack();
            order.markReadyForPickup();
            order.markOutForDelivery(ChangedBy.DELIVERY_PARTNER);
            order.markDelivered(ChangedBy.DELIVERY_PARTNER, Instant.now());
        });
    }
}
