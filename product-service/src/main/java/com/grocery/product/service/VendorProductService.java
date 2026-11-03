package com.grocery.product.service;

import com.grocery.product.dto.request.ProductRequest;
import com.grocery.product.dto.request.UpdateProductStatusRequest;
import com.grocery.product.dto.response.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Store panel: a vendor manages the products of THEIR OWN store.
 * Every method takes the vendor's storeId (X-Store-Id); products of other stores are never found.
 * (Listing and searching products is done by product-read-service.)
 */
public interface VendorProductService {

    ProductResponse createProduct(Long storeId, ProductRequest request);

    ProductResponse updateProduct(Long storeId, Long productId, ProductRequest request);

    /** ACTIVE or INACTIVE. A product blocked by an admin cannot be changed. */
    ProductResponse updateProductStatus(Long storeId, Long productId, UpdateProductStatusRequest request);

    /** Soft delete: the product disappears everywhere, but the row stays for history. */
    void deleteProduct(Long storeId, Long productId);

    ProductResponse addImage(Long storeId, Long productId, MultipartFile image);

    ProductResponse removeImage(Long storeId, Long productId, String imageUrl);
}
