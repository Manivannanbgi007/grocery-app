package com.grocery.order.invoice;

import com.grocery.order.config.InvoiceProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InvoiceStorageTest {

    private static final byte[] PDF = "%PDF-1.4 an invoice".getBytes(StandardCharsets.US_ASCII);

    @TempDir
    private Path tempDir;

    private InvoiceStorage storage() {
        return new InvoiceStorage(new InvoiceProperties(tempDir.resolve("invoices")));
    }

    @Test
    void save_writesTheFileIntoTheFolderOfTheOrdersMonth_andReadGivesItBack() throws IOException {
        // 31 Oct 20:00 UTC is already 1 Nov 01:30 in India
        String path = storage().save("INV-000123", Instant.parse("2026-10-31T20:00:00Z"), PDF);

        assertThat(path).isEqualTo("2026/11/INV-000123.pdf");
        assertThat(tempDir.resolve("invoices").resolve(path)).hasBinaryContent(PDF);
        assertThat(storage().read(path).orElseThrow()).isEqualTo(PDF);
        try (Stream<Path> files = Files.list(tempDir.resolve("invoices/2026/11"))) {
            assertThat(files).hasSize(1);   // no temporary file left behind
        }
    }

    @Test
    void save_replacesAnOlderFileOfTheSameInvoice() throws IOException {
        byte[] newer = "%PDF-1.4 generated again".getBytes(StandardCharsets.US_ASCII);
        storage().save("INV-000123", Instant.parse("2026-10-27T06:00:00Z"), PDF);

        String path = storage().save("INV-000123", Instant.parse("2026-10-27T06:00:00Z"), newer);

        assertThat(storage().read(path).orElseThrow()).isEqualTo(newer);
    }

    @Test
    void read_ofAMissingFile_isEmpty() {
        assertThat(storage().read("2026/10/INV-000999.pdf")).isEmpty();
    }

    @Test
    void aPathOutsideTheFolder_isRefused() throws IOException {
        Files.write(tempDir.resolve("other.pdf"), PDF);   // exists, but is not an invoice

        assertThat(storage().read("../other.pdf")).isEmpty();
        assertThatThrownBy(() -> storage().save("../../../other", Instant.parse("2026-10-27T06:00:00Z"), PDF))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void whenTheFolderCannotBeCreated_saveThrows_andTheStartUpCheckOnlyLogs() throws IOException {
        Files.writeString(tempDir.resolve("invoices"), "a file where the folder should be");

        assertThatThrownBy(() -> storage().save("INV-000123", Instant.parse("2026-10-27T06:00:00Z"), PDF))
                .isInstanceOf(IOException.class);
        assertThatCode(() -> storage().checkWritable()).doesNotThrowAnyException();
    }
}
