package com.grocery.product.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Where product images are kept. Today: the local disk (LocalImageStorageService).
 * Later an S3/MinIO implementation can replace it without changing any other code.
 */
public interface ImageStorageService {

    /** Saves the image and returns the URL clients use to load it. */
    String store(Long productId, MultipartFile file);

    /** Deletes an image saved by store(). URLs this service did not create are ignored. */
    void delete(String imageUrl);
}
