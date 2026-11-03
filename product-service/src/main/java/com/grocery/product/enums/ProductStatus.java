package com.grocery.product.enums;

/**
 * <pre>
 * ACTIVE   - visible to customers
 * INACTIVE - hidden by the vendor (e.g. out of season)
 * BLOCKED  - hidden by an admin (moderation); only an admin can unblock it
 * DELETED  - removed by the vendor (soft delete: the row stays for old orders and history)
 * </pre>
 */
public enum ProductStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED,
    DELETED
}
