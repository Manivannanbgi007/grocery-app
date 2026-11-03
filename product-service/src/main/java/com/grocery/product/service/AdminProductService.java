package com.grocery.product.service;

import com.grocery.product.dto.response.ProductResponse;

/**
 * Admin panel: product moderation.
 */
public interface AdminProductService {

    /** Hides a product from customers; the vendor cannot undo this. */
    ProductResponse blockProduct(Long productId);

    /** Makes a blocked product ACTIVE again. */
    ProductResponse unblockProduct(Long productId);
}
