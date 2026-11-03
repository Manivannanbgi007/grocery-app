-- The invoices that were generated and stored (InvoiceServiceImpl). One per order, and so one per payment: an order
-- is paid by exactly one payment. The PDF itself is a file under app.invoice.storage-dir; file_path is relative to it.
-- An invoice is stored only once the order is paid, so the stored PDF always shows the final, paid state.
CREATE TABLE invoices (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    order_id          BIGINT       NOT NULL COMMENT 'The order (and so the payment) the invoice belongs to',
    invoice_number    VARCHAR(20)  NOT NULL COMMENT 'INV-000123 for order 123',
    customer_user_id  BIGINT       NOT NULL COMMENT 'The customer the invoice is for',
    store_id          BIGINT       NOT NULL COMMENT 'The store that sold',
    payment_id        BIGINT       NULL     COMMENT 'The payment in payment-service; empty for older orders',
    file_path         VARCHAR(255) NOT NULL COMMENT 'Relative to app.invoice.storage-dir, e.g. 2026/10/INV-000123.pdf',
    file_size         BIGINT       NOT NULL COMMENT 'Bytes; with sha256 it detects a damaged or replaced file',
    sha256            VARCHAR(64)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    created_by        VARCHAR(50)  NULL,
    updated_by        VARCHAR(50)  NULL,
    version           BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_invoices_order UNIQUE (order_id),
    INDEX idx_invoices_customer (customer_user_id)
);
