package com.grocery.product.entity;

import com.grocery.product.enums.ProductStatus;
import com.grocery.product.enums.Unit;
import com.grocery.product.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * A product sold by ONE store (multi-tenant: every product has a storeId).
 *
 * This is the WRITE model (MySQL, the source of truth). Every change to this table is captured
 * by Debezium and copied into Elasticsearch by product-read-service, which serves all searches.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products")
public class Product extends BaseEntity {

    public static final int MAX_IMAGES = 5;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 60)
    private String brand;

    /** The vendor's own product code, unique within the store. */
    @Column(nullable = false, length = 40)
    private String sku;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Unit unit;

    /** e.g. 500 (with unit G) = "500 g". */
    @Column(name = "unit_quantity", nullable = false, precision = 10, scale = 3)
    private BigDecimal unitQuantity;

    /** Price before discount (MRP), in INR. Change it only through updatePricing(). */
    @Column(nullable = false, precision = 10, scale = 2)
    @Setter(AccessLevel.NONE)
    private BigDecimal price;

    @Column(name = "discount_percentage", nullable = false, precision = 5, scale = 2)
    @Setter(AccessLevel.NONE)
    private BigDecimal discountPercentage;

    /** What the customer pays = price minus discount. Always calculated, never set directly. */
    @Column(name = "selling_price", nullable = false, precision = 10, scale = 2)
    @Setter(AccessLevel.NONE)
    private BigDecimal sellingPrice;

    /** GST percentage (0, 5, 12, 18 or 28 in India). */
    @Column(name = "tax_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal taxPercentage;

    @Convert(converter = StringListConverter.class)
    @Column(name = "image_urls", nullable = false, columnDefinition = "JSON")
    private List<String> imageUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ProductStatus status;

    /** Average stars of the visible reviews (null without reviews); kept up to date by ProductReviewService. */
    @Column(name = "rating_average", precision = 2, scale = 1)
    private BigDecimal ratingAverage;

    @Column(name = "rating_count", nullable = false)
    private int ratingCount;

    // ----- reviews -----

    /** @param average e.g. 4.333..., stored as 4.3 */
    public void updateRating(Double average, long count) {
        this.ratingCount = (int) count;
        this.ratingAverage = count == 0 || average == null ? null : BigDecimal.valueOf(average).setScale(1, RoundingMode.HALF_UP);
    }

    // ----- pricing -----

    /** Sets price and discount together, so the selling price can never be out of date. */
    public void updatePricing(BigDecimal newPrice, BigDecimal newDiscountPercentage) {
        this.price = newPrice;
        this.discountPercentage = newDiscountPercentage;
        BigDecimal discount = newPrice.multiply(newDiscountPercentage).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        this.sellingPrice = newPrice.subtract(discount);
    }

    // ----- status rules -----

    /** A vendor can switch between ACTIVE and INACTIVE, but cannot undo an admin's block. */
    public void changeStatusByVendor(ProductStatus newStatus) {
        if (newStatus != ProductStatus.ACTIVE && newStatus != ProductStatus.INACTIVE) {
            throw new BusinessException("A vendor can only set a product to ACTIVE or INACTIVE");
        }
        if (status == ProductStatus.BLOCKED) {
            throw new BusinessException("This product was blocked by an admin. Please contact support.");
        }
        this.status = newStatus;
    }

    public void block() {
        if (status == ProductStatus.BLOCKED) {
            throw new BusinessException("The product is already blocked");
        }
        this.status = ProductStatus.BLOCKED;
    }

    public void unblock() {
        if (status != ProductStatus.BLOCKED) {
            throw new BusinessException("Only a BLOCKED product can be unblocked (current status: " + status + ")");
        }
        this.status = ProductStatus.ACTIVE;
    }

    public void markDeleted() {
        this.status = ProductStatus.DELETED;
    }

    // ----- images -----

    public void addImage(String imageUrl) {
        if (imageUrls.size() >= MAX_IMAGES) {
            throw new BusinessException("A product can have at most " + MAX_IMAGES + " images");
        }
        imageUrls.add(imageUrl);
    }

    public boolean removeImage(String imageUrl) {
        return imageUrls.remove(imageUrl);
    }
}
