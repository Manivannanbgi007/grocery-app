package com.grocery.order.config;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;

/**
 * Settings under "app.invoice" in application.yml.
 *
 * @param storageDir the folder for the stored invoice PDFs (env INVOICE_STORAGE_DIR). Keep it: invoices are documents
 *                   that must be kept for years, and back it up together with the database. With more than one
 *                   replica of order-service every replica must see the same folder (a shared volume).
 */
@Validated
@ConfigurationProperties(prefix = "app.invoice")
public record InvoiceProperties(@NotNull Path storageDir) {
}
