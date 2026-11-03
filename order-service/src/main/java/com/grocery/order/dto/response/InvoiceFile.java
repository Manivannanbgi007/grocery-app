package com.grocery.order.dto.response;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * A generated invoice: the file name and the PDF bytes.
 *
 * @param fileName e.g. "invoice-INV-000123.pdf"
 */
public record InvoiceFile(String fileName, byte[] content) {

    /** INV-000123 -> "invoice-INV-000123.pdf" */
    public static InvoiceFile of(String invoiceNumber, byte[] content) {
        return new InvoiceFile("invoice-" + invoiceNumber + ".pdf", content);
    }

    /** The HTTP answer that makes the browser download the PDF. */
    public static ResponseEntity<byte[]> asDownload(InvoiceFile invoice) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(invoice.fileName()).build().toString())
                .body(invoice.content());
    }
}
