package com.grocery.order.invoice;

import com.grocery.order.config.InvoiceProperties;
import com.grocery.order.logging.Loggable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Keeps the invoice PDFs as files in the folder app.invoice.storage-dir, one folder per year and month of the order:
 * 2026/10/INV-000123.pdf. The database stores this relative path, so the folder itself can move.
 *
 * A file is first written under a temporary name in the same folder, flushed to the disk, and only then renamed to
 * its real name in one step (an atomic move): nobody ever reads a half-written invoice, even after a crash.
 */
@Slf4j
@Component
@Loggable   // file access: LoggingAspect logs the time of every call and warns when it is slow
public class InvoiceStorage {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    private final Path folder;

    public InvoiceStorage(InvoiceProperties properties) {
        this.folder = properties.storageDir().toAbsolutePath().normalize();
    }

    /**
     * At start-up: create the folder and write a test file, so a wrong setting shows up in the log at once.
     * The service starts anyway: invoices are then generated for every request, but not stored.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void checkWritable() {
        try {
            Files.createDirectories(folder);
            Files.delete(Files.createTempFile(folder, ".write-check-", ".tmp"));
            log.info("Invoices are stored in {}", folder);
        } catch (IOException | RuntimeException ex) {
            log.error("Invoices cannot be stored in {} ({}): they are generated but not stored until this is fixed "
                    + "(app.invoice.storage-dir / INVOICE_STORAGE_DIR)", folder, ex.toString());
        }
    }

    /**
     * Saves the PDF and returns its path relative to the folder, for example "2026/10/INV-000123.pdf".
     *
     * @throws IOException when the file cannot be written (disk full, no permission ...)
     */
    public String save(String invoiceNumber, Instant orderCreatedAt, byte[] pdf) throws IOException {
        LocalDate orderDay = orderCreatedAt.atZone(INDIA).toLocalDate();
        String relativePath = "%d/%02d/%s.pdf".formatted(orderDay.getYear(), orderDay.getMonthValue(), invoiceNumber);
        Path file = resolve(relativePath);
        Files.createDirectories(file.getParent());

        Path temporary = Files.createTempFile(file.getParent(), invoiceNumber + "-", ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer content = ByteBuffer.wrap(pdf);
                while (content.hasRemaining()) {
                    channel.write(content);
                }
                channel.force(true);   // really on the disk before the rename
            }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);   // only still there when something failed
        }
        return relativePath;
    }

    /** The stored PDF, or empty when the file is missing or cannot be read. */
    public Optional<byte[]> read(String relativePath) {
        try {
            return Optional.of(Files.readAllBytes(resolve(relativePath)));
        } catch (IOException | RuntimeException ex) {
            log.debug("Invoice file {} cannot be read: {}", relativePath, ex.toString());
            return Optional.empty();
        }
    }

    /** A path inside the folder; anything that would point outside it (for example "../") is refused. */
    private Path resolve(String relativePath) {
        Path file = folder.resolve(relativePath).normalize();
        if (!file.startsWith(folder)) {
            throw new IllegalArgumentException("Not a path inside the invoice folder: " + relativePath);
        }
        return file;
    }
}
