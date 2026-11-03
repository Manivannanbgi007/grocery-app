package com.grocery.delivery.storage;

import com.grocery.delivery.config.MediaProperties;
import com.grocery.delivery.exception.BusinessException;
import com.grocery.delivery.logging.Loggable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Saves proof-of-delivery photos on the local disk (app.media.upload-dir) and returns their public URL.
 * Same approach as product images in product-service; a cloud store (S3/MinIO) can replace it later.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Loggable   // Writes uploaded photos to disk: LoggingAspect logs the time of every call and warns when it is slow
public class ProofPhotoStorage {

    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final MediaProperties mediaProperties;

    public String store(Long deliveryId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("The photo is empty");
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new BusinessException("Only JPEG, PNG or WEBP photos are allowed");
        }

        String relativePath = "proofs/" + deliveryId + "/" + UUID.randomUUID() + extension;
        Path target = mediaProperties.uploadDir().toAbsolutePath().normalize().resolve(relativePath);
        try (InputStream content = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save the photo", ex);
        }
        log.info("Saved proof of delivery {} for delivery id={}", target, deliveryId);
        return mediaProperties.publicBaseUrl() + "/" + relativePath;
    }
}
