package com.grocery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A generated and stored invoice of a paid order: which order, customer, store and payment it belongs to, and where
 * its PDF lies (relative to app.invoice.storage-dir). One per order (order_id is unique).
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only; use Invoice.of(...)
@Entity
@Table(name = "invoices")
public class Invoice extends BaseEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "invoice_number", nullable = false, length = 20, updatable = false)
    private String invoiceNumber;

    @Column(name = "customer_user_id", nullable = false, updatable = false)
    private Long customerUserId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private Long storeId;

    @Column(name = "payment_id", updatable = false)
    private Long paymentId;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(nullable = false, length = 64)
    private String sha256;

    public static Invoice of(Order order, String filePath, long fileSize, String sha256) {
        Invoice invoice = new Invoice();
        invoice.orderId = order.getId();
        invoice.invoiceNumber = order.getInvoiceNumber();
        invoice.customerUserId = order.getCustomerUserId();
        invoice.storeId = order.getStoreId();
        invoice.paymentId = order.getPaymentId();
        invoice.useFile(filePath, fileSize, sha256);
        return invoice;
    }

    /** The PDF was missing or damaged and has been generated and stored again. */
    public void useFile(String filePath, long fileSize, String sha256) {
        this.filePath = filePath;
        this.fileSize = fileSize;
        this.sha256 = sha256;
    }
}
