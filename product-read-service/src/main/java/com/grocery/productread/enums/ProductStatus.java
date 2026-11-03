package com.grocery.productread.enums;

/**
 * Same values as product-service's ProductStatus. Customers only ever see ACTIVE products.
 */
public enum ProductStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED,
    DELETED
}
