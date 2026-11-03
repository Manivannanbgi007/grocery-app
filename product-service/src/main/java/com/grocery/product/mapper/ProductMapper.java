package com.grocery.product.mapper;

import com.grocery.product.dto.request.ProductRequest;
import com.grocery.product.dto.response.ProductResponse;
import com.grocery.product.entity.Category;
import com.grocery.product.entity.Product;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Converts between Product entities and DTOs. Only copies fields - business rules stay in Product.
 */
@Component
public class ProductMapper {

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getStoreId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getDescription(),
                product.getBrand(),
                product.getSku(),
                product.getUnit(),
                product.getUnitQuantity(),
                product.getPrice(),
                product.getDiscountPercentage(),
                product.getSellingPrice(),
                product.getTaxPercentage(),
                List.copyOf(product.getImageUrls()),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public void copyDetails(ProductRequest request, Category category, Product product) {
        product.setCategory(category);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setBrand(request.brand());
        product.setSku(request.sku().toUpperCase());
        product.setUnit(request.unit());
        product.setUnitQuantity(request.unitQuantity());
        product.setTaxPercentage(request.taxPercentage());
        product.updatePricing(request.price(), request.discountOrZero());
    }
}
