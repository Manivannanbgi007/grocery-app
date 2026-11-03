package com.grocery.order.service.impl;

import com.grocery.order.dto.response.InvoiceFile;
import com.grocery.order.entity.Invoice;
import com.grocery.order.entity.Order;
import com.grocery.order.exception.BusinessException;
import com.grocery.order.exception.InvoiceUnavailableException;
import com.grocery.order.exception.ResourceNotFoundException;
import com.grocery.order.invoice.InvoicePdfGenerator;
import com.grocery.order.invoice.InvoiceStorage;
import com.grocery.order.repository.InvoiceRepository;
import com.grocery.order.repository.OrderRepository;
import com.grocery.order.service.InvoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Generates an invoice once, stores it and then reuses the stored file.
 *
 *   A  short read-only transaction: load the order (with the caller's access check) and its invoice record.
 *      Not paid yet -> generate a fresh PDF, do not store it (cash on delivery: the payment line still changes).
 *   -  no transaction: a record exists -> read the file, check its size and SHA-256 -> send it   (the usual case)
 *   B  otherwise, a short write transaction: lock the order row (SELECT ... FOR UPDATE) so only one request per
 *      order can be here, check everything again, generate -> write the file -> save the record -> send it.
 *
 * Step B never changes the order itself, so the payment saga (which uses the order's @Version) is not affected.
 * The two steps are separate transactions on purpose: the file is read without holding a database connection,
 * and step B uses READ COMMITTED so its checks after the lock see what other requests just committed.
 */
@Slf4j
@Service
public class InvoiceServiceImpl implements InvoiceService {

    private final InvoicePdfGenerator invoicePdfGenerator;
    private final InvoiceStorage invoiceStorage;
    private final InvoiceRepository invoiceRepository;
    private final OrderRepository orderRepository;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate writeTransaction;

    public InvoiceServiceImpl(InvoicePdfGenerator invoicePdfGenerator, InvoiceStorage invoiceStorage,
                              InvoiceRepository invoiceRepository, OrderRepository orderRepository,
                              PlatformTransactionManager transactionManager) {
        this.invoicePdfGenerator = invoicePdfGenerator;
        this.invoiceStorage = invoiceStorage;
        this.invoiceRepository = invoiceRepository;
        this.orderRepository = orderRepository;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.writeTransaction = new TransactionTemplate(transactionManager);
        this.writeTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /** What step A found: a fresh PDF of an unpaid order, or the paid order's id and its record (null if none). */
    private record FirstLook(InvoiceFile unpaidPdf, Long orderId, Invoice stored) {
    }

    @Override
    public InvoiceFile getInvoice(Supplier<Order> findOrderWithAccessCheck) {
        // A: the order and its invoice record
        FirstLook look = readTransaction.execute(status -> {
            Order order = findOrderWithAccessCheck.get();
            invoicePdfGenerator.checkHasInvoice(order);
            if (!order.isPaid()) {
                return new FirstLook(generate(order), order.getId(), null);
            }
            return new FirstLook(null, order.getId(), invoiceRepository.findByOrderId(order.getId()).orElse(null));
        });
        if (look.unpaidPdf() != null) {
            return look.unpaidPdf();
        }

        // The usual case: the stored file
        if (look.stored() != null) {
            Optional<InvoiceFile> storedFile = readStoredFile(look.stored());
            if (storedFile.isPresent()) {
                return storedFile.get();
            }
        }

        // B: the first request (or the file was lost): generate and store, one request per order at a time
        try {
            return writeTransaction.execute(status -> generateAndStore(look.orderId()));
        } catch (DataIntegrityViolationException ex) {
            // Not expected while the order row is locked; the unique key on order_id is the last guard against a
            // second record. Run step B again: it now finds the stored invoice.
            log.warn("Invoice of order id={} was stored by another request at the same time; using that one", look.orderId());
            return writeTransaction.execute(status -> generateAndStore(look.orderId()));
        }
    }

    private InvoiceFile generateAndStore(Long orderId) {
        // Waits here while another request generates the same invoice, then sees its result below
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        invoicePdfGenerator.checkHasInvoice(order);   // it may have been cancelled in the meantime
        if (!order.isPaid()) {
            return generate(order);
        }
        Optional<Invoice> stored = invoiceRepository.findByOrderId(orderId);
        if (stored.isPresent()) {
            Optional<InvoiceFile> storedFile = readStoredFile(stored.get());
            if (storedFile.isPresent()) {
                return storedFile.get();   // another request stored it while this one waited for the lock
            }
        }

        InvoiceFile pdf = generate(order);
        String filePath;
        try {
            filePath = invoiceStorage.save(order.getInvoiceNumber(), order.getCreatedAt(), pdf.content());
        } catch (IOException | RuntimeException ex) {
            // The customer still gets the PDF; nothing is recorded, so the next request tries to store it again
            log.error("Invoice {} of order id={} could not be stored; it was sent without storing it",
                    order.getInvoiceNumber(), orderId, ex);
            return pdf;
        }

        String sha256 = sha256(pdf.content());
        if (stored.isPresent()) {
            stored.get().useFile(filePath, pdf.content().length, sha256);
        } else {
            invoiceRepository.save(Invoice.of(order, filePath, pdf.content().length, sha256));
        }
        log.info("Invoice {} of order id={} (payment id={}) generated and stored as {}",
                order.getInvoiceNumber(), orderId, order.getPaymentId(), filePath);
        return pdf;
    }

    /** The stored PDF, if the file is there and unchanged (same size and SHA-256 as when it was stored). */
    private Optional<InvoiceFile> readStoredFile(Invoice invoice) {
        Optional<byte[]> content = invoiceStorage.read(invoice.getFilePath());
        if (content.isEmpty()) {
            log.warn("Invoice file {} of order id={} is missing; generating it again", invoice.getFilePath(), invoice.getOrderId());
            return Optional.empty();
        }
        if (content.get().length != invoice.getFileSize() || !sha256(content.get()).equals(invoice.getSha256())) {
            log.warn("Invoice file {} of order id={} is damaged (size or checksum changed); generating it again",
                    invoice.getFilePath(), invoice.getOrderId());
            return Optional.empty();
        }
        return Optional.of(InvoiceFile.of(invoice.getInvoiceNumber(), content.get()));
    }

    /** A business rule (no invoice yet) stays a 400; anything unexpected from the PDF library becomes a 503. */
    private InvoiceFile generate(Order order) {
        try {
            return invoicePdfGenerator.generate(order);
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new InvoiceUnavailableException(ex);
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Every Java runtime has SHA-256", ex);
        }
    }
}
