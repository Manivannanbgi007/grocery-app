package com.grocery.product.service.impl;

import com.grocery.product.config.MediaProperties;
import com.grocery.product.exception.BusinessException;
import com.grocery.product.service.ImageStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Saves images under {uploadDir}/products/{productId}/{random-name}.jpg.
 * MediaConfig serves that folder at /media/**, and the gateway routes /media/** here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocalImageStorageService implements ImageStorageService {

    /** Allowed content types and the file extension we save them with. */
    private static final Map<String, String> ALLOWED_TYPES = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final MediaProperties mediaProperties;

    @Override
    public String store(Long productId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException("The image file is empty");
        }
        String extension = ALLOWED_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new BusinessException("Only JPEG, PNG or WEBP images are allowed");
        }

        String relativePath = "products/" + productId + "/" + UUID.randomUUID() + extension;
        Path target = uploadDir().resolve(relativePath);
        try (InputStream content = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(content, target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not save the image", ex);
        }

        log.info("Saved image for product id={} at {}", productId, target);
        return mediaProperties.publicBaseUrl() + "/" + relativePath;
    }

    @Override
    public void delete(String imageUrl) {
        String prefix = mediaProperties.publicBaseUrl() + "/";
        if (imageUrl == null || !imageUrl.startsWith(prefix)) {
            return;
        }
        Path file = uploadDir().resolve(imageUrl.substring(prefix.length())).normalize();
        if (!file.startsWith(uploadDir())) {
            return;   // never delete anything outside the upload folder
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.warn("Could not delete image {}", file, ex);
        }
    }

    private Path uploadDir() {
        return mediaProperties.uploadDir().toAbsolutePath().normalize();
    }
}
