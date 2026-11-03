package com.grocery.product.repository;

import com.grocery.product.entity.Product;
import com.grocery.product.enums.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Write-side repository. Searching and listing products is done by product-read-service (Elasticsearch).
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    /** A vendor's own product that is not deleted. Products of other stores are never found (multi-tenant rule). */
    Optional<Product> findByIdAndStoreIdAndStatusNot(Long id, Long storeId, ProductStatus status);

    boolean existsByStoreIdAndSkuIgnoreCaseAndStatusNot(Long storeId, String sku, ProductStatus status);

    /** Any product that is not deleted (customers review products of any store). */
    Optional<Product> findByIdAndStatusNot(Long id, ProductStatus status);

    boolean existsByStoreIdAndSkuIgnoreCaseAndStatusNotAndIdNot(Long storeId, String sku, ProductStatus status, Long id);
}
