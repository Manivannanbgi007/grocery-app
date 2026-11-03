package com.grocery.productread.service.impl;

import com.grocery.productread.document.CategoryDocument;
import com.grocery.productread.document.ProductDocument;
import com.grocery.productread.document.StockDocument;
import com.grocery.productread.enums.ProductStatus;
import com.grocery.productread.mapper.CatalogChangeMapper;
import com.grocery.productread.repository.CategoryDocumentRepository;
import com.grocery.productread.repository.ProductDocumentRepository;
import com.grocery.productread.repository.StockDocumentRepository;
import com.grocery.productread.service.CatalogIndexService;
import com.grocery.productread.util.AvroFields;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.generic.GenericRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.VersionConflictException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogIndexServiceImpl implements CatalogIndexService {

    private static final int PAGE_SIZE = 500;

    private final ProductDocumentRepository productRepository;
    private final CategoryDocumentRepository categoryRepository;
    private final StockDocumentRepository stockRepository;
    private final CatalogChangeMapper changeMapper;

    @Override
    public void applyProductChange(GenericRecord productRow) {
        Long productId = AvroFields.getLong(productRow, "id");

        // Deleted products (soft or hard delete) leave the read model completely
        if (AvroFields.isDeleted(productRow)
                || ProductStatus.DELETED.name().equals(AvroFields.getString(productRow, "status"))) {
            productRepository.deleteById(productId);
            log.info("Product id={} removed from the index", productId);
            return;
        }

        String categoryName = categoryRepository.findById(AvroFields.getLong(productRow, "category_id"))
                .map(CategoryDocument::getName)
                .orElse(null);   // the category may arrive a moment later; applyCategoryChange then fills it in
        ProductDocument product = changeMapper.toProductDocument(productRow, categoryName);
        // the stock may also arrive later; applyStockChange then fills it in
        changeMapper.copyStock(stockRepository.findById(productId).orElse(null), product);

        if (saveIfNotStale(product, productRepository::save)) {
            log.info("Indexed product id={} version={} status={}", productId, product.getVersion(), product.getStatus());
        }
    }

    @Override
    public void applyStockChange(GenericRecord stockChanged) {
        StockDocument stock = changeMapper.toStockDocument(stockChanged);
        if (!saveIfNotStale(stock, stockRepository::save)) {
            return;   // older than the stock we already have
        }
        log.info("Stored stock of product id={} version={}: {} available", stock.getId(), stock.getVersion(), stock.getAvailableQuantity());

        // Copy it onto the product, keeping the product's own version (EXTERNAL_GTE allows the same version).
        // No product yet? applyProductChange copies the stock when the product arrives.
        productRepository.findById(stock.getId()).ifPresent(product -> {
            changeMapper.copyStock(stock, product);
            saveIfNotStale(product, productRepository::save);
        });
    }

    @Override
    public void applyCategoryChange(GenericRecord categoryRow) {
        Long categoryId = AvroFields.getLong(categoryRow, "id");
        if (AvroFields.isDeleted(categoryRow)) {
            categoryRepository.deleteById(categoryId);
            log.info("Category id={} removed from the index", categoryId);
            return;
        }

        CategoryDocument category = changeMapper.toCategoryDocument(categoryRow);
        if (saveIfNotStale(category, categoryRepository::save)) {
            log.info("Indexed category id={} version={} name={}", categoryId, category.getVersion(), category.getName());
            refreshCategoryNameOnProducts(category);
        }
    }

    /**
     * Products store a copy of their category's name (so they can be found by it).
     * After a category change, update that copy. The product's own version is kept,
     * which EXTERNAL_GTE allows ("same version" is not older).
     */
    private void refreshCategoryNameOnProducts(CategoryDocument category) {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE);
        Page<ProductDocument> page;
        int updated = 0;
        do {
            page = productRepository.findByCategoryId(category.getId(), pageable);
            for (ProductDocument product : page) {
                if (!Objects.equals(product.getCategoryName(), category.getName())) {
                    product.setCategoryName(category.getName());
                    if (saveIfNotStale(product, productRepository::save)) {
                        updated++;
                    }
                }
            }
            pageable = pageable.next();
        } while (page.hasNext());

        if (updated > 0) {
            log.info("Updated the category name on {} products of category id={}", updated, category.getId());
        }
    }

    /**
     * Elasticsearch refuses a document whose version is older than the stored one.
     * Such a change is outdated (a newer one was already applied), so it is skipped - not an error.
     */
    private <T> boolean saveIfNotStale(T document, Consumer<T> save) {
        try {
            save.accept(document);
            return true;
        } catch (VersionConflictException ex) {
            log.info("Skipped an outdated change: {}", ex.getMessage());
            return false;
        }
    }
}
