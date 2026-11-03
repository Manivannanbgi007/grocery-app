package com.grocery.productread.mapper;

import com.grocery.productread.document.CategoryDocument;
import com.grocery.productread.document.ProductDocument;
import com.grocery.productread.dto.response.CategoryResponse;
import com.grocery.productread.dto.response.ProductResponse;
import com.grocery.productread.enums.ProductStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Elasticsearch documents -> API responses.
 */
@Component
public class ProductDocumentMapper {

    public ProductResponse toResponse(ProductDocument product) {
        return new ProductResponse(
                product.getId(),
                product.getStoreId(),
                product.getCategoryId(),
                product.getCategoryName(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getSku(),
                product.getUnit(),
                quantity(product.getUnitQuantity()),
                money(product.getPrice()),
                money(product.getDiscountPercentage()),
                money(product.getSellingPrice()),
                money(product.getTaxPercentage()),
                product.getImageUrls() == null ? List.of() : product.getImageUrls(),
                ProductStatus.valueOf(product.getStatus()),
                product.getAvailableQuantity(),
                product.isInStock(),
                product.isLowStock(),
                product.getRatingAverage(),
                product.getRatingCount(),
                product.getUpdatedAt());
    }

    public CategoryResponse toResponse(CategoryDocument category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                category.getDescription(),
                category.getImageUrl(),
                category.getDisplayOrder(),
                category.isActive());
    }

    /** Elasticsearch stores numbers as floating point: give money its 2 decimals back (45.0 -> 45.00). */
    private BigDecimal money(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }

    /** 500.000 -> 500, 0.250 -> 0.25 */
    private BigDecimal quantity(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }
}
