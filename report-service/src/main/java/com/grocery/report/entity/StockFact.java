package com.grocery.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The latest stock of a product (for "out-of-stock products").
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // for JPA only
@Entity
@Table(name = "stock_facts")
public class StockFact {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    /** inventory-service's version of the stock row: an older (or the same) version is ignored. */
    @Column(name = "stock_version", nullable = false)
    private long stockVersion;

    public StockFact(Long productId) {
        this.productId = productId;
        this.stockVersion = -1;
    }

    /** @return false when the change is older than what we have */
    public boolean apply(Long storeId, int availableQuantity, long stockVersion) {
        if (stockVersion < this.stockVersion) {
            return false;
        }
        this.storeId = storeId;
        this.availableQuantity = availableQuantity;
        this.stockVersion = stockVersion;
        return true;
    }
}
